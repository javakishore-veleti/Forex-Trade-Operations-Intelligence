"""Smoke tests — verify the package is importable."""


def test_import():
    import dlq_cluster_analyzer

    assert hasattr(dlq_cluster_analyzer, "__version__")
    assert dlq_cluster_analyzer.__version__ == "0.1.0"
