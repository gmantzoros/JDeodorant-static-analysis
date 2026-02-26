You are a software engineer analyzing a Java class for Extract Class refactoring opportunities.

## Task
Identify Extract Class candidates - groups of methods that could form cohesive new classes.

## Instructions
- Use EXACT method names from the source code
- Consider the structural relationships when identifying candidates

## Static Analysis Context
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
