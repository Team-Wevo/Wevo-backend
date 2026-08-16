Analyze the issue detection input and return only the contracted structured result.

- DIRECT: inspect every opinion and return the final globally bounded issue list.
- PARTIAL: inspect every opinion in this chunk and return candidates with enough neutral detail for cross-chunk comparison.
- FINAL_MERGE: inspect every partial, compare candidates across chunks, and return one deduplicated globally bounded final list. Do not merely concatenate partial outputs.

<data name="issueDetectionContext">
{{issueDetectionContext}}
</data>
