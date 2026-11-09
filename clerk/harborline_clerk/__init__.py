"""A clerk proposes. An examiner admits. The desk writes."""

from harborline_clerk.loop import consult
from harborline_clerk.proposal import Admitted, Refusal

__all__ = ["Admitted", "Refusal", "consult"]
