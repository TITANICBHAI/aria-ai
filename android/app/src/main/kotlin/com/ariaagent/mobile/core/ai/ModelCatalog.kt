package com.ariaagent.mobile.core.ai

/**
 * ModelCatalog — single source of truth for all downloadable LLM/VLM models.
 *
 * Each entry describes one GGUF model the user can download and activate as
 * ARIA's primary inference model. Vision-capable models optionally include an
 * mmproj file so LlamaEngine.loadVision() can use them for screen understanding.
 *
 * URL verification: confirm filenames on HuggingFace before first release.
 * File sizes are approximate lower-bounds used for download-complete detection.
 */
data class CatalogModel(
    /** Stable identifier stored in SharedPreferences. Never change once shipped. */
    val id: String,
    val displayName: String,
    val description: String,
    /** Filename written to the models directory on device. */
    val filename: String,
    /** HuggingFace (or other) direct download URL for the GGUF. */
    val url: String,
    /** Minimum expected byte count — used to confirm download is complete. */
    val expectedSizeBytes: Long,
    /** CLIP projection filename, required for vision inference (null = text-only). */
    val mmprojFilename: String? = null,
    /** Download URL for the mmproj GGUF. */
    val mmprojUrl: String? = null,
    /** Approximate on-disk size in MB shown in the UI. */
    val displaySizeMb: Int = (expectedSizeBytes / 1_048_576L).toInt(),
)

object ModelCatalog {

    // ── Llama 3.2 1B (default, text-only) ────────────────────────────────────

    val LLAMA_32_1B = CatalogModel(
        id                = "llama-3.2-1b",
        displayName       = "Llama 3.2 1B",
        description       = "General-purpose 1B text model. Fast, lightweight — great for reasoning and chat on any Android device.",
        filename          = "llama-3.2-1b-q4_k_m.gguf",
        url               = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/" +
                            "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
        expectedSizeBytes = 780_000_000L,
        displaySizeMb     = 744,
    )

    // ── Moondream2 (vision + text) ────────────────────────────────────────────
    // Source: vikhyatk/moondream2 on HuggingFace
    // ~1.86B params — Q4_K_M ≈ 1.0 GB base + 90 MB mmproj

    val MOONDREAM2 = CatalogModel(
        id                = "moondream2",
        displayName       = "Moondream2",
        description       = "Tiny but capable vision-language model. Excellent at describing what is on screen with very low RAM usage.",
        filename          = "moondream2-q4_k_m.gguf",
        url               = "https://huggingface.co/vikhyatk/moondream2/resolve/main/" +
                            "moondream2-Q4_K_M.gguf",
        expectedSizeBytes = 950_000_000L,
        displaySizeMb     = 906,
        mmprojFilename    = "moondream2-mmproj-f16.gguf",
        mmprojUrl         = "https://huggingface.co/vikhyatk/moondream2/resolve/main/" +
                            "mmproj-moondream2-f16.gguf",
    )

    // ── SmolVLM 500M (vision + text) ──────────────────────────────────────────
    // Source: ggml-org/SmolVLM-500M-Instruct-GGUF
    // ~500M params — Q4_K_M ≈ 350 MB base + 65 MB mmproj

    val SMOLVLM_500M = CatalogModel(
        id                = "smolvlm-500m",
        displayName       = "SmolVLM 500M",
        description       = "500M multimodal model from HuggingFace. Compact, fast vision-language model that fits easily on mid-range Android phones.",
        filename          = "smolvlm-500m-q4_k_m.gguf",
        url               = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/" +
                            "SmolVLM-500M-Instruct-Q4_K_M.gguf",
        expectedSizeBytes = 330_000_000L,
        displaySizeMb     = 315,
        mmprojFilename    = "smolvlm-500m-mmproj-f16.gguf",
        mmprojUrl         = "https://huggingface.co/ggml-org/SmolVLM-500M-Instruct-GGUF/resolve/main/" +
                            "mmproj-SmolVLM-500M-Instruct-f16.gguf",
    )

    // ── Qwen2.5-VL 3B (vision + text, larger) ────────────────────────────────
    // Source: Qwen/Qwen2.5-VL-3B-Instruct-GGUF
    // ~3B params — Q4_K_M ≈ 2.0 GB. Vision encoder is embedded, no separate mmproj.

    val QWEN25_VL_3B = CatalogModel(
        id                = "qwen2.5-vl-3b",
        displayName       = "Qwen2.5-VL 3B",
        description       = "3B vision-language model from Alibaba. Strong reasoning + vision. Requires more RAM — best on 6 GB+ devices.",
        filename          = "qwen2.5-vl-3b-q4_k_m.gguf",
        url               = "https://huggingface.co/Qwen/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/" +
                            "Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf",
        expectedSizeBytes = 1_900_000_000L,
        displaySizeMb     = 1812,
    )

    // ── Registry ──────────────────────────────────────────────────────────────

    val ALL: List<CatalogModel> = listOf(LLAMA_32_1B, MOONDREAM2, SMOLVLM_500M, QWEN25_VL_3B)

    const val DEFAULT_ID = "llama-3.2-1b"

    fun findById(id: String): CatalogModel? = ALL.find { it.id == id }
}
