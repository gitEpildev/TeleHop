"""Reject malformed or ambiguous YAML anywhere in the packaged backend resources."""
from pathlib import Path

import yaml


class UniqueKeys(yaml.SafeLoader):
    def construct_mapping(self, node, deep=False):
        result = super().construct_mapping(node, deep)
        if len(result) != len(node.value):
            raise ValueError(f"Duplicate mapping key near line {node.start_mark.line + 1}")
        return result


if __name__ == "__main__":
    resources = Path(__file__).resolve().parents[2] / "paper/src/main/resources"
    paths = sorted(resources.rglob("*.yml"))
    if not paths:
        raise SystemExit(f"No YAML resources found under {resources}")
    for path in paths:
        try:
            if not isinstance(yaml.load(path.read_bytes(), Loader=UniqueKeys), dict):
                raise ValueError("Expected a top-level mapping")
        except (ValueError, yaml.YAMLError) as error:
            raise SystemExit(f"{path}: {error}")
    print(f"Validated {len(paths)} backend YAML resources, including all deployment profiles.")
