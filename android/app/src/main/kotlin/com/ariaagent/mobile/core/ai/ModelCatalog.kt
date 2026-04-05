package com.ariaagent.mobile.core.ai

/**
 * ModelCatalog — single source of truth for all downloadable LLM/VLM models.
 *
 * All 4 entries are multimodal (vision + text) so ARIA can understand the screen
 * in every mode. URLs and filenames are verified against the live HuggingFace repos
 * as of April 2025 — do not edit without re-checking the repo file listing.
 *
 * Model  │ Repo (base file)                               │ Size Q4_K_M
 * ───────┼────────────────────────────────────────────────┼────────────
 * 256M   │ ggml-org/SmolVLM-256M-Instruct-GGUF            │ ~125 MB
 * 500M   │ Mungert/SmolVLM-500M-Instruct-GGUF             │ ~289 MB
 * Moon.2 │ cjpais/moondream2-llamafile (pure GGUF Q5_K)   │ ~1.06 GB
 * Qwen3B │ ggml-org/Qwen2.5-VL-3B-Instruct-GGUF           │ ~1.93 GB
 *
 * mmproj files are always F16 (quantising them causes visible image quality loss).
 *
 * DEFAULT_ID is smolvlm-256m — the fastest, lowest-RAM model, most suitable as
 * a first-boot default on mid-range devices like the Samsung Galaxy M31.
 */
data class CatalogModel(
    /** Stable identifier stored in SharedPreferences. Never change once shipped. */
    val id: String,
    val displayName: String,
    val description: String,
    /** Filename written to the models directory on device. */
    val filename: String,
    /** Direct HTTPS download URL for the GGUF (follows HF redirects automatically). */
    val url: String,
    /** Minimum expected byte count — used to confirm download is complete. */
    val expectedSizeBytes: Long,
    /** CLIP projection filename required for vision inference (null = text-only). */
    val mmprojFilename: String? = null,
    /** Download URL for the mmproj GGUF. Null only if model has no vision head. */
    val mmprojUrl: String? = null,
    /** Approximate combined on-disk size in MB shown in the UI. */
    val displaySizeMb: Int = (expectedSizeBytes / 1_048_576L).toInt(),
)

object ModelCatalog {

    // ── SmolVLM 256M (smallest VLM — default model) ───────────────────────────
    // Repo:    ggml-org/SmolVLM-256M-Instruct-GGUF
    //          ggml-org/SmolVLM-256M-Instruct-GGUF (mmproj)
    // Params:  ~256M  │  Q4_K_M ≈ 125 MB + 98 MB mmproj
    // RAM:     under 1 GB — works on any Android phone

    val SMOLVLM_256M = CatalogModel(
        id                = "smolvlm-256m",
        displayName       = "SmolVLM 256M",
        description       = "World's smallest multimodal model. Understands images and text with under 1 GB RAM. Ideal default for any Android phone.",
        filename          = "smolvlm-256m-q4_k_m.gguf",
        url               = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/" +
                            "SmolVLM-256M-Instruct-Q4_K_M.gguf",
        expectedSizeBytes = 120_000_000L,
        displaySizeMb     = 220,
        mmprojFilename    = "smolvlm-256m-mmproj-f16.gguf",
        mmprojUrl         = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/main/" +
                            "mmproj-SmolVLM-256M-Instruct-f16.gguf",
    )

    // ── SmolVLM 500M (mid-range VLM) ──────────────────────────────────────────
    // Repo:    Mungert/SmolVLM-500M-Instruct-GGUF   (Q4_K_M base)
    //          ggml-org/SmolVLM-500M-Instruct-GGUF  (mmproj F16)
    // Params:  ~500M  │  Q4_K_M ≈ 289 MB + 199 MB mmproj
    // RAM:     ~1.2 GB

    val SMOLVLM_500M = CatalogModel(
        id                = "smolvlm-500m",
        displayName       = "SmolVLM 500M",
        description       = "500M multimodal model. Better vision understanding than 256M with still very low RAM — great for mid-range devices.",
        filename          = "smolvlm-500m-q4_k_m.gguf",
        url               = "https://huggingface.co/Mungert/SmolVLM-500M-Instruct-GGUF/resolve/main/" +
                            "SmolVLM-500M-Instruct-q4_k_m.gguf",
        expectedSizeBytes = 280_000_000L,
        displaySizeMb     = 475,
        mmprojFilename    = "smolvlm-500m-mmproj-f16.gguf",
        mmprojUrl         = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/" +
                            "mmproj-SmolVLM-500M-Instruct-f16.gguf",
    )

    // ── Moondream2 (compact VLM with strong image understanding) ──────────────
    // Repo:    cjpais/moondream2-llamafile (pure GGUF files, not the llamafile)
    // Params:  ~1.86B │  Q5_K ≈ 1.06 GB + 910 MB mmproj
    // RAM:     ~2.0 GB — fits Galaxy M31 (6 GB RAM)
    // Note:    No Q4_K_M exists for Moondream2; Q5_K is the smallest available quant.

    val MOONDREAM2 = CatalogModel(
        id                = "moondream2",
        displayName       = "Moondream2",
        description       = "Compact 1.86B vision-language model. Excellent at describing screens and answering questions about images with low RAM usage.",
        filename          = "moondream2-q5k.gguf",
        url               = "https://huggingface.co/cjpais/moondream2-llamafile/resolve/main/" +
                            "moondream2-050824-q5k.gguf",
        expectedSizeBytes = 1_000_000_000L,
        displaySizeMb     = 1900,
        mmprojFilename    = "moondream2-mmproj-f16.gguf",
        mmprojUrl         = "https://huggingface.co/cjpais/moondream2-llamafile/resolve/main/" +
                            "moondream2-mmproj-050824-f16.gguf",
    )

    // ── Qwen2.5-VL 3B (most capable on-device VLM) ────────────────────────────
    // Repo:    ggml-org/Qwen2.5-VL-3B-Instruct-GGUF   (Q4_K_M base)
    //          Mungert/Qwen2.5-VL-3B-Instruct-GGUF    (mmproj F16)
    // Params:  ~3B    │  Q4_K_M ≈ 1.93 GB + ~500 MB mmproj
    // RAM:     ~3 GB — requires 6 GB+ device (Galaxy M31 minimum)

    val QWEN25_VL_3B = CatalogModel(
        id                = "qwen2.5-vl-3b",
        displayName       = "Qwen2.5-VL 3B",
        description       = "Most powerful on-device vision model. Strong reasoning, document understanding, and screen interaction. Needs 6 GB+ RAM.",
        filename          = "qwen2.5-vl-3b-q4_k_m.gguf",
        url               = "https://huggingface.co/ggml-org/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/" +
                            "qwen2.5-vl-3b-instruct-q4_k_m.gguf",
        expectedSizeBytes = 1_900_000_000L,
        displaySizeMb     = 2300,
        mmprojFilename    = "qwen2.5-vl-3b-mmproj-f16.gguf",
        mmprojUrl         = "https://huggingface.co/Mungert/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/" +
                            "Qwen2.5-VL-3B-Instruct-mmproj-f16.gguf",
    )

    // ── Registry ──────────────────────────────────────────────────────────────

    val ALL: List<CatalogModel> = listOf(SMOLVLM_256M, SMOLVLM_500M, MOONDREAM2, QWEN25_VL_3B)

    /** Default is the smallest model — works on any device, fast first-boot. */
    const val DEFAULT_ID = "smolvlm-256m"

    fun findById(id: String): CatalogModel? = ALL.find { it.id == id }
}
