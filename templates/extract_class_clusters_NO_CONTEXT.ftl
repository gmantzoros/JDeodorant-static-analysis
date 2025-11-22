Task: Identify Logical Responsibility Clusters

---

Goal:
Analyze the provided source code and structural metrics and determine how the class should be decomposed into smaller responsibility-based clusters.

Important:
- Do NOT output any code.
- Do NOT output explanations, rationale, or comments.
- Output ONLY the clusters of methods grouped by the conceptual classes you recommend.
- Every cluster MUST contain only method names exactly as they appear in the source code.
- Use the output format shown below exactly.


Original Source Code:
${sourceCode}

---

Output Format (strict):
Return a JSON object where each key is a new conceptual class name
and each value is a list of method names from the original class.

Example:
{
  "ClassA": ["method1", "method2"],
  "ClassB": ["method3"]
}

---

Produce only the JSON object and nothing else.
