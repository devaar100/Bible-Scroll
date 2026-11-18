from types import SimpleNamespace

from pipeline.usage import OUTPUT_WATCH, output_stats, request_log


def message(output, thinking, read=0, write=0, stop="end_turn"):
    usage = SimpleNamespace(output_tokens=output, output_tokens_details=SimpleNamespace(thinking_tokens=thinking),
                            cache_read_input_tokens=read, cache_creation_input_tokens=write)
    return SimpleNamespace(usage=usage, stop_reason=stop)


def test_request_log_splits_thinking_from_written_tokens():
    log = request_log("JHN.3.16", message(5000, 3800, read=1200, write=0))
    assert log == {"id": "JHN.3.16", "stopReason": "end_turn", "outputTokens": 5000, "thinkingTokens": 3800,
                   "writtenTokens": 1200, "cacheReadTokens": 1200, "cacheWriteTokens": 0}


def test_request_log_without_thinking_details():
    usage = SimpleNamespace(output_tokens=900, cache_read_input_tokens=None, cache_creation_input_tokens=None)
    log = request_log("X", SimpleNamespace(usage=usage, stop_reason="end_turn"))
    assert (log["thinkingTokens"], log["writtenTokens"], log["cacheReadTokens"]) == (0, 900, 0)


def test_output_stats_max_median_and_count_above_watch():
    logs = [request_log(str(i), message(o, t, stop=s)) for i, (o, t, s) in
            enumerate([(3000, 2000, "end_turn"), (9000, 8000, "end_turn"), (16000, 15000, "max_tokens")])]
    stats = output_stats(logs)
    assert stats["maxOutputTokens"] == 16000 and stats["medianOutputTokens"] == 9000
    assert stats[f"above{OUTPUT_WATCH}"] == 2 and stats["hitMaxTokens"] == 1
    assert stats["medianThinkingTokens"] == 8000
    assert output_stats([]) == {"requests": 0}
