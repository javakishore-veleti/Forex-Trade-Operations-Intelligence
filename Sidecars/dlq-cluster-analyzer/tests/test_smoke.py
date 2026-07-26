"""Tests for dlq-cluster-analyzer clustering logic."""


def test_import():
    import dlq_cluster_analyzer

    assert hasattr(dlq_cluster_analyzer, "__version__")
    assert dlq_cluster_analyzer.__version__ == "0.1.0"


def test_same_traces_cluster_together():
    from dlq_cluster_analyzer.detector import StackTraceClusterer

    clusterer = StackTraceClusterer(min_cluster_size=2)

    trace = "java.lang.NullPointerException\n  at com.fxtradeops.Service.process(Service.java:42)"
    id1 = clusterer.ingest(trace, "FX-000001")
    id2 = clusterer.ingest(trace, "FX-000002")

    assert id1 == id2
    clusters = clusterer.get_significant_clusters()
    assert len(clusters) == 1
    assert clusters[0].count == 2


def test_different_traces_separate_clusters():
    from dlq_cluster_analyzer.detector import StackTraceClusterer

    clusterer = StackTraceClusterer(min_cluster_size=1)

    trace_a = "java.lang.NullPointerException\n  at com.fxtradeops.ServiceA.run(ServiceA.java:10)"
    trace_b = "java.io.IOException\n  at com.fxtradeops.ServiceB.connect(ServiceB.java:55)"

    id_a = clusterer.ingest(trace_a, "FX-000001")
    id_b = clusterer.ingest(trace_b, "FX-000002")

    assert id_a != id_b
    clusters = clusterer.get_significant_clusters()
    assert len(clusters) == 2


def test_line_numbers_normalized():
    from dlq_cluster_analyzer.detector import StackTraceClusterer

    clusterer = StackTraceClusterer(min_cluster_size=2)

    trace_a = "java.lang.RuntimeException\n  at com.fxtradeops.Svc.call(Svc.java:42)"
    trace_b = "java.lang.RuntimeException\n  at com.fxtradeops.Svc.call(Svc.java:99)"

    id_a = clusterer.ingest(trace_a, "FX-000001")
    id_b = clusterer.ingest(trace_b, "FX-000002")

    # Should cluster together since only line numbers differ
    assert id_a == id_b


def test_min_cluster_size_filtering():
    from dlq_cluster_analyzer.detector import StackTraceClusterer

    clusterer = StackTraceClusterer(min_cluster_size=5)

    trace = "Exception at Service.java:10"
    for i in range(3):
        clusterer.ingest(trace, f"FX-{i:06d}")

    # Only 3 entries, min is 5 — should not appear
    clusters = clusterer.get_significant_clusters()
    assert len(clusters) == 0


def test_reset_clears_clusters():
    from dlq_cluster_analyzer.detector import StackTraceClusterer

    clusterer = StackTraceClusterer(min_cluster_size=1)
    clusterer.ingest("some.Exception", "FX-000001")
    assert len(clusterer.get_significant_clusters()) == 1

    clusterer.reset()
    assert len(clusterer.get_significant_clusters()) == 0
