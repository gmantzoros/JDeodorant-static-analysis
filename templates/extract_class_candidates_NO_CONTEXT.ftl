Task: Identify Extract Class refactoring CANDIDATES within the provided class.

Goal:
Produce a set of method clusters, where each cluster corresponds to a potential Extract Class refactoring candidate.

Important:
- Output ONLY clusters of method names.
- Use EXACT method names as they appear in the source code.
- Do NOT output code.
- Do NOT explain your reasoning.
- Do NOT produce a full decomposition of the class.
- Do NOT include every method—include ONLY methods that form a meaningful refactoring candidate.
- Clusters should be SMALL and based on strong structural signals.
- A method may appear in more than one cluster if justified.
- Clusters do NOT need to cover all methods.

Extract Class Candidate Rules (strict):
- A cluster must represent a cohesive subset of methods with high internal interaction:
  • strong call relationships, OR
  • strong shared field usage, OR
  • clearly isolated functionality inside the class
- Do NOT force disjoint partitions; clusters may overlap.
- Do NOT create conceptual modules or domain groups.
- Do NOT group all methods into clusters.

---

Original Source Code:
${sourceCode}

---

Output Format (strict):
Return a JSON object where each key is a candidate class name
and each value is a list of method names that form a viable Extract Class refactoring candidate.

Example:
{
  "Candidate1": ["methodA"],
  "Candidate2": ["methodA", "methodB"],
  "Candidate3": ["methodC", "methodD"]
}

Produce ONLY the JSON object.
