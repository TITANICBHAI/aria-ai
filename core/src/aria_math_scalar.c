/*
 * aria_math_scalar.c — portable scalar math fallback
 *
 * Implements the math functions declared in aria_c_api.h using pure C.
 * No SIMD, no platform-specific headers.
 *
 * Selected by core/CMakeLists.txt when neither aarch64 nor x86_64 is
 * detected. Also useful for tests and fuzzing since it is always correct.
 */

#include "aria_c_api.h"
#include <math.h>
#include <string.h>

static float dot_scalar(const float* a, const float* b, int n)
{
    float acc = 0.0f;
    for (int i = 0; i < n; i++) acc += a[i] * b[i];
    return acc;
}

static float l2_norm_scalar(const float* v, int n)
{
    return sqrtf(dot_scalar(v, v, n));
}

float aria_cosine_similarity(const float* a, const float* b, int n)
{
    float dot   = dot_scalar(a, b, n);
    float normA = l2_norm_scalar(a, n);
    float normB = l2_norm_scalar(b, n);
    float denom = normA * normB;
    return (denom > 1e-8f) ? (dot / denom) : 0.0f;
}

void aria_l2_normalize(float* v, int n)
{
    float norm = l2_norm_scalar(v, n);
    if (norm <= 1e-8f) return;
    for (int i = 0; i < n; i++) v[i] /= norm;
}

float aria_dot_product(const float* a, const float* b, int n)
{
    return dot_scalar(a, b, n);
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
    for (int i = 0; i < n; i++) logits[i] *= invSum;
}

void aria_mat_vec_relu(const float* W, const float* x, float* out,
                       int rows, int cols)
{
    for (int i = 0; i < rows; i++) {
        float val = dot_scalar(W + i * cols, x, cols);
        out[i] = val > 0.0f ? val : 0.0f;
    }
}
