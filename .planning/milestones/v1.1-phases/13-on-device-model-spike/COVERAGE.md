# API Coverage — LiteRT-LM Android (`com.google.ai.edge.litertlm:litertlm-android`, 0.17.1 / fallback 0.16.1)

> Full coverage by default. Opt-outs are explicit, reasoned decisions. Surface read from the AAR via `javap` (13-RESEARCH.md
> "Sources"). Integrated in `spike-ondevice` (plans 13-01, 13-04, 13-07) and, on the green_ship branch only, `:ondevice` (13-11).

| capability | decision | reason |
|---|---|---|
| engine_initialize_cpu | INTEGRATE | |
| engine_initialize_gpu | INTEGRATE | |
| engine_initialize_npu | OPT-OUT | explicitly out of scope: no prebuilt NPU model exists for the S22's sm8450 (prebuilts target sm8550/8650/8750/8850, qcs8275) |
| engine_config_max_num_tokens | INTEGRATE | |
| engine_config_cache_dir | INTEGRATE | |
| engine_close | INTEGRATE | |
| conversation_send_message | INTEGRATE | |
| conversation_send_message_async_streaming | OPT-OUT | not needed: SingleShot acts on one complete tool call; time to first token comes from BenchmarkInfo |
| conversation_config_system_instruction | INTEGRATE | |
| conversation_config_sampler_config | INTEGRATE | |
| conversation_config_thinking_config | INTEGRATE | |
| conversation_config_prefill_preface_on_init | INTEGRATE | |
| conversation_config_enable_response_format | INTEGRATE | |
| response_format_json | INTEGRATE | |
| response_format_regex | OPT-OUT | not needed: tool arguments are JSON objects; the JSON format covers Route A |
| tools_open_api_tool | INTEGRATE | |
| automatic_tool_calling | OPT-OUT | explicitly out of scope: the engine never executes tools; writes go only through the app's PreApplyGate (automaticToolCalling false) |
| message_tool_calls_read | INTEGRATE | |
| benchmark_info | INTEGRATE | |
| experimental_flags_enable_benchmark | INTEGRATE | |
| experimental_flags_constrained_decoding | INTEGRATE | |
| capabilities_supports_function_calling | OPT-OUT | not needed: Route B support is measured directly per screen cell, and the call is absent from the 0.16.1 fallback pin |
| session_run_prefill_run_decode | OPT-OUT | not needed: the provider uses the Conversation API; KV reuse is measured at Conversation level because no clone API exists |
| multimodal_contents_image_audio | OPT-OUT | explicitly out of scope: the engine is STT-agnostic and receives text transcripts only |
| embeddings | OPT-OUT | explicitly out of scope: SingleShot does no retrieval; not present in the 0.16.1 fallback pin |
| benchmark_cli_benchmark_kt | OPT-OUT | not needed: per-call BenchmarkInfo gives prefill and decode facts inside the real engine path |

# API Coverage — Hugging Face Hub (model file download, plan 13-06)

> Second integration against a different need (weights delivery), decided from the same full-coverage baseline.

| capability | decision | reason |
|---|---|---|
| resolve_download_pinned_revision | INTEGRATE | |
| sha256_size_verification | INTEGRATE | |
| authenticated_gated_download | OPT-OUT | explicitly out of scope for the agent: Gemma 3 1B is gated under the Gemma Terms; only Yahir may accept terms and download (13-08 user_setup) |
| model_metadata_api | OPT-OUT | not needed: revision, sizes and digests were read once in 13-RESEARCH.md and pinned in the runner |
| upload_or_write | OPT-OUT | explicitly out of scope: nothing is published to the Hub |
