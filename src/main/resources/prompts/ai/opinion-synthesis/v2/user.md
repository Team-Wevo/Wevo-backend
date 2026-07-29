아래 JSON data를 분석해 합의점과 쟁점을 구조화해 주세요.

- mode가 PARTIAL이면 opinions의 모든 의견과 gapAnswers를 검토합니다.
- mode가 FINAL_MERGE이면 partials를 chunkIndex 순서로 모두 검토해 하나의 최종 결과로 병합합니다.
  최종 coveredOpinionIds는 모든 partials.coveredOpinionIds의 합집합과 정확히 같아야 합니다.
  부분 결과를 그대로 나열하지 말고 중복을 제거한 일관된 합의점과 쟁점으로 다시 작성합니다.

<synthesis_data>
<data name="synthesisContext">{{synthesisContext}}</data>
</synthesis_data>
