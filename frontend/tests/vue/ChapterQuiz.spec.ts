import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ChapterQuiz from '../../src/features/reader/ChapterQuiz.vue'
const quiz = {
  questions: [
    {
      sentence: 'The station is a ____ depot.',
      options: ['small', 'large'],
      correctAnswer: 'small',
      explanation: 'The passage describes its size.',
    },
    { question: 'Choose a place.', options: ['station', 'garden'], answer: 'station' },
  ],
}
describe('chapter quiz interaction', () => {
  it('hides feedback until all choices are submitted and shows per-question results', async () => {
    const wrapper = mount(ChapterQuiz, { props: { quiz } })
    expect(wrapper.find('.quiz-feedback').exists()).toBe(false)
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await wrapper.findAll('input')[0]!.setValue()
    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    await wrapper.findAll('input')[3]!.setValue()
    await wrapper.get('form').trigger('submit')
    expect(wrapper.get('[role="status"]').text()).toContain('1 / 2')
    expect(wrapper.findAll('.quiz-feedback')[0]!.text()).toContain('回答正确')
    expect(wrapper.findAll('.quiz-feedback')[1]!.text()).toContain('回答错误')
    expect(wrapper.text()).toContain('正确答案：station')
    expect(wrapper.text()).toContain('解析：The passage describes its size.')
    expect(wrapper.findAll('input').every((x) => x.attributes('disabled') !== undefined)).toBe(true)
    await wrapper.get('button').trigger('click')
    expect(wrapper.find('.quiz-feedback').exists()).toBe(false)
    expect(wrapper.findAll('input').every((x) => !(x.element as HTMLInputElement).checked)).toBe(
      true,
    )
  })
  it('resets answers when a regenerated quiz replaces the current questions', async () => {
    const wrapper = mount(ChapterQuiz, { props: { quiz } })
    await wrapper.findAll('input')[0]!.setValue()
    await wrapper.findAll('input')[2]!.setValue()
    await wrapper.get('form').trigger('submit')
    await wrapper.setProps({
      quiz: { questions: [{ sentence: 'New question', options: ['a', 'b'], correctAnswer: 'b' }] },
    })
    expect(wrapper.find('.quiz-feedback').exists()).toBe(false)
    expect(wrapper.text()).toContain('已完成 0 / 1')
    expect(wrapper.findAll('input').every((x) => !(x.element as HTMLInputElement).checked)).toBe(
      true,
    )
  })
})
