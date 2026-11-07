# Chat prompt version 1

Runtime prompt: `backend/src/main/resources/prompts/chat-v1.txt`. Version `1` is stored with each turn. It requires tool-backed facts, clarification for ambiguous scope, explicit partial failures and untrusted-data handling. Final output selects evidence references rather than unchecked factual prose. Backend code independently enforces permissions, typed arguments, deadlines, call counts, evidence references and mapping rules even if the model ignores the prompt.

No held-out questions/expected answers are included. The deterministic verification adapter is scripted and is never described as a real model. Prompt changes require the same adversarial and source-backed verification as the orchestration code; the prompt is not an authorization mechanism.
