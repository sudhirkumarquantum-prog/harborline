"""Ranks architecture decisions with TF-IDF. The corpus is the ADR set."""

from __future__ import annotations

import math
import re
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

_TOKEN = re.compile(r"[a-z0-9]+")


def tokenize(text: str) -> list[str]:
    return _TOKEN.findall(text.lower())


@dataclass(frozen=True)
class Hit:
    name: str
    score: float


class Catalog:
    def __init__(self, documents: dict[str, str]) -> None:
        if not documents:
            raise ValueError("catalog needs at least one decision")
        self._counts = {name: Counter(tokenize(text)) for name, text in documents.items()}
        self._length = {name: sum(counts.values()) or 1 for name, counts in self._counts.items()}
        self._df: Counter[str] = Counter()
        for counts in self._counts.values():
            self._df.update(counts.keys())
        self._n = len(documents)

    @classmethod
    def from_directory(cls, path: Path) -> Catalog:
        documents = {
            entry.name: entry.read_text()
            for entry in sorted(path.glob("*.md"))
        }
        return cls(documents)

    def ask(self, query: str, limit: int = 3) -> list[Hit]:
        terms = Counter(tokenize(query))
        scored: list[Hit] = []
        for name, counts in self._counts.items():
            score = 0.0
            for term, weight in terms.items():
                if term not in counts:
                    continue
                idf = math.log((self._n + 1) / (self._df[term] + 1)) + 1.0
                score += (counts[term] / self._length[name]) * idf * weight
            if score > 0:
                scored.append(Hit(name, score))
        scored.sort(key=lambda hit: (-hit.score, hit.name))
        return scored[:limit]
