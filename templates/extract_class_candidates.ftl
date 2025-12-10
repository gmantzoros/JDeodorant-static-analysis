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

Context:

Class Name: ${className}

Methods:
<#if methods?size gt 0>
<#list methods as m>
• ${m.name}
  (calls: <#if m.calls?size gt 0><#list m.calls as c>${c}<#if c_has_next>, </#if></#list><#else>-</#if>,
   called by: <#if m.calledBy?size gt 0><#list m.calledBy as cb>${cb}<#if cb_has_next>, </#if></#list><#else>-</#if>)
</#list>
<#else>
No methods.
</#if>

Fields and Usage:
<#if fields?size gt 0>
<#list fields as f>
• ${f.name} (read by: <#if f.readBy?size gt 0><#list f.readBy as r>${r}<#if r_has_next>,</#if></#list><#else>-</#if>,
              written by: <#if f.writtenBy?size gt 0><#list f.writtenBy as w>${w}<#if w_has_next>,</#if></#list><#else>-</#if>)
</#list>
<#else>
No fields.
</#if>

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
