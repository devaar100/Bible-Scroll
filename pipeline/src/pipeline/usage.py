"""Token usage and USD cost of Claude API calls (generator and rubric)."""
from __future__ import annotations

import statistics

from dataclasses import dataclass

#: USD per million tokens: (input, output, cache write 5m, cache read). Haiku 5.5 rates are for prompts
#: up to 100K tokens, which every study prompt is. A 1-hour cache write costs 2x input.
PRICES = {
    "claude-haiku-5-5": (0.10, 0.50, 0.125, 0.01),
    "claude-sonnet-5-5": (2.00, 10.00, 2.50, 0.10),
    "claude-opus-5-5": (4.00, 20.00, 5.00, 0.20),
}
#: The Message Batches API bills every token type at half price.
BATCH_DISCOUNT = 0.5


@dataclass
class Usage:
    input_tokens: int = 0
    output_tokens: int = 0
    cache_creation_input_tokens: int = 0
    cache_read_input_tokens: int = 0
    requests: int = 0
    cache_write_1h_tokens: int = 0  # the part of cache_creation_input_tokens written with the 1-hour TTL

    def add(self, usage) -> None:
        self.requests += 1
        for name in ("input_tokens", "output_tokens", "cache_creation_input_tokens", "cache_read_input_tokens"):
            setattr(self, name, getattr(self, name) + (getattr(usage, name, 0) or 0))
        breakdown = getattr(usage, "cache_creation", None)
        self.cache_write_1h_tokens += (getattr(breakdown, "ephemeral_1h_input_tokens", 0) or 0) if breakdown else 0

    def merge(self, other: "Usage") -> None:
        for name in vars(other):
            setattr(self, name, getattr(self, name) + getattr(other, name))

    def cost(self, model: str, batch: bool = False) -> float | None:
        if model not in PRICES:
            return None
        i, o, cw, cr = PRICES[model]
        write_5m = self.cache_creation_input_tokens - self.cache_write_1h_tokens
        total = (self.input_tokens * i + self.output_tokens * o + write_5m * cw + self.cache_write_1h_tokens * 2 * i
                 + self.cache_read_input_tokens * cr) / 1_000_000
        return total * (BATCH_DISCOUNT if batch else 1.0)

    def as_dict(self, model: str, batch: bool = False) -> dict:
        cost = self.cost(model, batch)
        return {"requests": self.requests, "inputTokens": self.input_tokens, "outputTokens": self.output_tokens,
                "cacheWriteTokens": self.cache_creation_input_tokens,
                "cacheWrite1hTokens": self.cache_write_1h_tokens,
                "cacheReadTokens": self.cache_read_input_tokens,
                "costUsd": round(cost, 4) if cost is not None else None}

    @classmethod
    def from_dict(cls, d: dict) -> "Usage":
        return cls(input_tokens=d.get("inputTokens", 0), output_tokens=d.get("outputTokens", 0),
                   cache_creation_input_tokens=d.get("cacheWriteTokens", 0),
                   cache_read_input_tokens=d.get("cacheReadTokens", 0), requests=d.get("requests", 0),
                   cache_write_1h_tokens=d.get("cacheWrite1hTokens", 0))


# ---- Per-request output log (batch and direct mode) ----

OUTPUT_WATCH = 8000  # summaries count requests above this many output tokens (the old batch max_tokens)


def request_log(study_id: str, message) -> dict:
    """One request's token use: thinking vs written output, and cache reads/writes."""
    u = message.usage
    output = getattr(u, "output_tokens", 0) or 0
    details = getattr(u, "output_tokens_details", None)
    thinking = (getattr(details, "thinking_tokens", 0) or 0) if details else 0
    return {"id": study_id, "stopReason": message.stop_reason, "outputTokens": output, "thinkingTokens": thinking,
            "writtenTokens": output - thinking, "cacheReadTokens": getattr(u, "cache_read_input_tokens", 0) or 0,
            "cacheWriteTokens": getattr(u, "cache_creation_input_tokens", 0) or 0}


def output_stats(requests: list[dict]) -> dict:
    """Max, median and count above OUTPUT_WATCH of total output tokens (and thinking) across requests."""
    if not requests:
        return {"requests": 0}
    out = [r["outputTokens"] for r in requests]
    thinking = [r["thinkingTokens"] for r in requests]
    return {"requests": len(out), "maxOutputTokens": max(out), "medianOutputTokens": statistics.median(out),
            f"above{OUTPUT_WATCH}": sum(1 for o in out if o > OUTPUT_WATCH),
            "maxThinkingTokens": max(thinking), "medianThinkingTokens": statistics.median(thinking),
            "hitMaxTokens": sum(1 for r in requests if r["stopReason"] == "max_tokens")}
