"""Smoke tests for log_normalizer package."""

from log_normalizer import __version__


def test_version():
    """Package version is set."""
    assert __version__ == "0.1.0"
