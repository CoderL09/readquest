<script setup lang="ts">
import { computed, ref, watch } from 'vue'
export interface ChapterQuizData {
  mainIdea?: string
  keySentence?: string
  questions?: Array<
    | string
    | {
        question?: string
        sentence?: string
        options?: string[]
        answer?: string
        correctAnswer?: string
        explanation?: string
      }
  >
}
const props = defineProps<{ quiz: ChapterQuizData }>()
const choices = ref<Record<number, string>>({})
const submitted = ref(false)
const questions = computed(() => props.quiz.questions ?? [])
const selectable = computed(() =>
  questions.value.flatMap((question, index) =>
    typeof question !== 'string' && question.options?.length ? [{ question, index }] : [],
  ),
)
const answered = computed(
  () => selectable.value.filter(({ index }) => choices.value[index] !== undefined).length,
)
const score = computed(
  () =>
    selectable.value.filter(
      ({ question, index }) => choices.value[index] === (question.correctAnswer || question.answer),
    ).length,
)
function reset() {
  choices.value = {}
  submitted.value = false
}
watch(() => props.quiz, reset)
</script>
<template>
  <form class="chapter-quiz stack" @submit.prevent="submitted = true">
    <h3 v-if="quiz.mainIdea">{{ quiz.mainIdea }}</h3>
    <blockquote v-if="quiz.keySentence" class="quote">{{ quiz.keySentence }}</blockquote>
    <p v-if="selectable.length" class="muted">
      已完成 {{ answered }} / {{ selectable.length }} 题 · 提交后查看答案
    </p>
    <fieldset v-for="(question, index) in questions" :key="index" class="quiz-question">
      <legend>
        {{ index + 1 }}.
        {{ typeof question === 'string' ? question : question.question || question.sentence }}
      </legend>
      <template v-if="typeof question !== 'string'">
        <label
          v-for="(option, optionIndex) in question.options"
          :key="optionIndex"
          class="quiz-option"
          :class="{ selected: choices[index] === option }"
        >
          <input
            v-model="choices[index]"
            type="radio"
            :name="`chapter-quiz-${index}`"
            :value="option"
            :disabled="submitted"
          />
          <span class="quiz-letter">{{ String.fromCharCode(65 + optionIndex) }}</span
          ><span>{{ option }}</span>
        </label>
        <div
          v-if="submitted"
          class="quiz-feedback"
          :class="{ incorrect: choices[index] !== (question.correctAnswer || question.answer) }"
        >
          <p>
            <strong>{{
              question.options?.length
                ? choices[index] === (question.correctAnswer || question.answer)
                  ? '回答正确'
                  : '回答错误'
                : '参考答案'
            }}</strong>
          </p>
          <p v-if="question.correctAnswer || question.answer">
            正确答案：{{ question.correctAnswer || question.answer }}
          </p>
          <p v-if="question.explanation">解析：{{ question.explanation }}</p>
        </div>
      </template>
    </fieldset>
    <p v-if="submitted && selectable.length" role="status">
      本次答对 {{ score }} / {{ selectable.length }} 题
    </p>
    <button
      v-if="questions.length && !submitted"
      class="button"
      type="submit"
      :disabled="answered < selectable.length"
    >
      提交答案
    </button>
    <button v-if="submitted" class="button secondary" type="button" @click="reset">重新作答</button>
  </form>
</template>
<style scoped>
.quiz-question {
  min-width: 0;
  margin: 0;
  padding: 1rem;
  border: 1px solid var(--line);
  border-radius: 16px;
}
.quiz-question legend {
  padding: 0 0.35rem;
  font-weight: 600;
  line-height: 1.6;
}
.quiz-option {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  padding: 0.65rem 0.75rem;
  margin-top: 0.5rem;
  min-height: 44px;
  border: 1px solid var(--line);
  border-radius: 10px;
  cursor: pointer;
  font-size: 1rem;
  font-weight: 400;
  overflow-wrap: anywhere;
}
.quiz-option.selected {
  border-color: var(--accent);
  background: var(--accent-soft, #e8efdf);
}
.quiz-option input[type='radio'] {
  appearance: auto;
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
  min-height: 18px;
  padding: 0;
  margin: 0;
  accent-color: var(--accent);
}
.quiz-letter {
  font-size: 0.8rem;
  color: var(--muted);
}
.quiz-feedback {
  margin-top: 1rem;
  padding: 0.75rem;
  border-left: 3px solid var(--accent);
  background: #edf3e7;
  border-radius: 4px;
}
.quiz-feedback.incorrect {
  background: #fff1e7;
  border-color: #9b5835;
}
.quiz-feedback p {
  margin: 0.25rem 0;
}
</style>
