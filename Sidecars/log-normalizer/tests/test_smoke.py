"""Tests for log-normalizer fact extraction logic."""


def test_import():
    import log_normalizer

    assert hasattr(log_normalizer, "__version__")
    assert log_normalizer.__version__ == "0.1.0"


def test_extract_timestamp_and_level():
    from log_normalizer.detector import LogFactExtractor

    extractor = LogFactExtractor()
    line = "2025-07-25T10:30:15.123Z INFO  Processing trade FX-000042"
    fact = extractor.extract(line)

    assert fact.timestamp == "2025-07-25T10:30:15.123Z"
    assert fact.level == "INFO"


def test_extract_trade_id():
    from log_normalizer.detector import LogFactExtractor

    extractor = LogFactExtractor()
    line = "2025-07-25 10:30:15 ERROR Failed to process trade FX-000123 due to timeout"
    fact = extractor.extract(line)

    assert fact.trade_id == "FX-000123"
    assert fact.level == "ERROR"


def test_extract_exception_class():
    from log_normalizer.detector import LogFactExtractor

    extractor = LogFactExtractor()
    line = "2025-07-25T10:30:15Z ERROR java.lang.NullPointerException at Service.call"
    fact = extractor.extract(line)

    assert fact.exception_class == "java.lang.NullPointerException"


def test_extract_correlation_id():
    from log_normalizer.detector import LogFactExtractor

    extractor = LogFactExtractor()
    line = "INFO correlationId=abc12345-def6-7890 Processing request"
    fact = extractor.extract(line)

    assert fact.correlation_id == "abc12345-def6-7890"


def test_extract_service_name():
    from log_normalizer.detector import LogFactExtractor

    extractor = LogFactExtractor()
    line = "2025-07-25T10:30:15Z INFO trade-lifecycle completed FX-000001"
    fact = extractor.extract(line)

    assert fact.service == "trade-lifecycle"
    assert fact.trade_id == "FX-000001"


def test_extract_key_value_pairs():
    from log_normalizer.detector import LogFactExtractor

    extractor = LogFactExtractor()
    line = "INFO status=COMPLETED duration=150ms tradeId=FX-000099"
    fact = extractor.extract(line)

    assert "status" in fact.extracted_fields
    assert fact.extracted_fields["status"] == "COMPLETED"
    assert fact.extracted_fields["duration"] == "150ms"


def test_batch_extraction():
    from log_normalizer.detector import LogFactExtractor

    extractor = LogFactExtractor()
    lines = [
        "2025-07-25T10:30:15Z INFO Processing FX-000001",
        "2025-07-25T10:30:16Z ERROR Failed FX-000002",
        "",  # empty line should be skipped
        "2025-07-25T10:30:17Z WARN Slow query for FX-000003",
    ]
    facts = extractor.extract_batch(lines)

    assert len(facts) == 3
    assert facts[0].trade_id == "FX-000001"
    assert facts[1].level == "ERROR"
    assert facts[2].level == "WARN"
