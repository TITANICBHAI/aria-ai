/*
 * aria_math_x86.c — SSE2/AVX2 SIMD math for x86-64 (Windows / Linux)
 *
 * Implements the math functions declared in aria_c_api.h using SSE2 intrinsics
 * as the baseline (guaranteed on all x86-64 CPUs including Ivy Bridge i3-3217U).
 *
 * AVX2 path is compiled only when -mavx2 is available and called only if the
 * CPU supports it (runtime CPUID check via __builtin_cpu_supports on GCC/Clang,
 * or IsProcessorFeaturePresent on MSVC). Falls back to SSE2 silently.
 *
 * SSE2: 4 floats per instruction — ~4× speedup over scalar for large vectors.
 * AVX2: 8 floats per instruction — ~8× speedup where available.
 */

#include "aria_c_api.h"
#include <math.h>
#include <string.h>

#if defined(_MSC_VER)
#  include <intrin.h>
#  include <immintrin.h>
#elif defined(__GNUC__) || defined(__clang__)
#  include <immintrin.h>
#endif

/* ─── SSE2 dot product ───────────────────────────────────────────────────── */
static float dot_sse2(const float* __restrict__ a,
                      const float* __restrict__ b,
                      int n)
{
    __m128 acc = _mm_setzero_ps();
    int i = 0;

    for (; i <= n - 4; i += 4) {
        __m128 va = _mm_loadu_ps(a + i);
        __m128 vb = _mm_loadu_ps(b + i);
        acc = _mm_add_ps(acc, _mm_mul_ps(va, vb));
    }

    /* Horizontal sum of 4-lane register */
    __m128 shuf = _mm_movehdup_ps(acc);
    __m128 sums = _mm_add_ps(acc, shuf);
    shuf = _mm_movehl_ps(shuf, sums);
    sums = _mm_add_ss(sums, shuf);
    float result = _mm_cvtss_f32(sums);

    /* Scalar tail */
    for (; i < n; i++) result += a[i] * b[i];
    return result;
}

/* ─── AVX2 dot product (compiled separately, called only if CPUID confirms) */
#if defined(__AVX2__) || (defined(_MSC_VER) && defined(__AVX2__))
static float dot_avx2(const float* __restrict__ a,
                      const float* __restrict__ b,
                      int n)
{
    __m256 acc = _mm256_setzero_ps();
    int i = 0;

    for (; i <= n - 8; i += 8) {
        __m256 va = _mm256_loadu_ps(a + i);
        __m256 vb = _mm256_loadu_ps(b + i);
        acc = _mm256_fmadd_ps(va, vb, acc);
    }

    /* Reduce 256-bit → scalar */
    __m128 lo  = _mm256_castps256_ps128(acc);
    __m128 hi  = _mm256_extractf128_ps(acc, 1);
    __m128 sum = _mm_add_ps(lo, hi);
    __m128 shuf = _mm_movehdup_ps(sum);
    __m128 s2   = _mm_add_ps(sum, shuf);
    shuf = _mm_movehl_ps(shuf, s2);
    s2   = _mm_add_ss(s2, shuf);
    float result = _mm_cvtss_f32(s2);

    /* Scalar tail */
    for (; i < n; i++) result += a[i] * b[i];
    return result;
}
#endif /* __AVX2__ */

/* ─── Runtime dispatch ───────────────────────────────────────────────────── */
static int has_avx2(void)
{
    static int cached = -1;
    if (cached >= 0) return cached;

#if defined(__GNUC__) || defined(__clang__)
    cached = __builtin_cpu_supports("avx2") ? 1 : 0;
#elif defined(_MSC_VER)
    #include <Windows.h>
    cached = IsProcessorFeaturePresent(PF_AVX2_INSTRUCTIONS_AVAILABLE) ? 1 : 0;
#else
    cached = 0;
#endif
    return cached;
}

static float dot_dispatch(const float* a, const float* b, int n)
{
#if defined(__AVX2__) || (defined(_MSC_VER) && defined(__AVX2__))
    if (has_avx2()) return dot_avx2(a, b, n);
#else
    (void)has_avx2;
#endif
    return dot_sse2(a, b, n);
}

static float l2_norm(const float* v, int n)
{
    return sqrtf(dot_dispatch(v, v, n));
}

/* ─── Public C API implementations ───────────────────────────────────────── */

float aria_cosine_similarity(const float* a, const float* b, int n)
{
    float dot   = dot_dispatch(a, b, n);
    float normA = l2_norm(a, n);
    float normB = l2_norm(b, n);
    float denom = normA * normB;
    return (denom > 1e-8f) ? (dot / denom) : 0.0f;
}

void aria_l2_normalize(float* v, int n)
{
    float norm = l2_norm(v, n);
    if (norm <= 1e-8f) return;
    float inv = 1.0f / norm;

    __m128 vInv = _mm_set1_ps(inv);
    int i = 0;
    for (; i <= n - 4; i += 4)
        _mm_storeu_ps(v + i, _mm_mul_ps(_mm_loadu_ps(v + i), vInv));
    for (; i < n; i++)
        v[i] *= inv;
}

float aria_dot_product(const float* a, const float* b, int n)
{
    return dot_dispatch(a, b, n);
}

void aria_softmax(float* logits, int n)
{
    if (n <= 0) return;

    float maxVal = logits[0];
    for (int i = 1; i < n; i++) {
        if (logits[i] > maxVal) maxVal = logits[i];
    }

    float sum = 0.0f;
    for (int i = 0; i < n; i++) {
        logits[i] = expf(logits[i] - maxVal);
        sum += logits[i];
    }

    float invSum = (sum > 1e-8f) ? (1.0f / sum) : (1.0f / n);
    __m128 vInv = _mm_set1_ps(invSum);
    int i = 0;
    for (; i <= n - 4; i += 4)
        _mm_storeu_ps(logits + i, _mm_mul_ps(_mm_loadu_ps(logits + i), vInv));
    for (; i < n; i++)
        logits[i] *= invSum;
}

void aria_mat_vec_relu(const float* W, const float* x, float* out,
                       int rows, int cols)
{
    for (int i = 0; i < rows; i++) {
        float val = dot_dispatch(W + i * cols, x, cols);
        out[i] = val > 0.0f ? val : 0.0f;
    }
}
