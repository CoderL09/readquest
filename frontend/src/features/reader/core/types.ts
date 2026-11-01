export type GrammarRole =
  | 'subject'
  | 'predicate'
  | 'object'
  | 'complement'
  | 'adverbial'
  | 'attribute'
  | 'clause'
  | 'coordination'

export type CoreSyntaxRole =
  'subject' | 'verb' | 'object' | 'complement' | 'indirectObject' | 'supporting'

export interface PhraseGroup {
  id: string
  text: string
  grammarRole?: GrammarRole
  coreRole?: CoreSyntaxRole
  roleHint?: string
}

export interface AnnotatedSentence {
  id: string
  original: string
  phraseGroups: PhraseGroup[]
}

export interface ReadingParagraph {
  id: string
  index: number
  sentences: AnnotatedSentence[]
}

export interface ReadingPage {
  pageIndex: number
  paragraphs: ReadingParagraph[]
}

export type AnnotationMode = 'original' | 'structure' | 'rhythm'
export type ReadingDisplayMode = 'original' | 'phrase'
export type ReaderFont =
  | 'literary'
  | 'serif'
  | 'humanist'
  | 'sans'
  | 'mono'
  | 'systemSans'
  | 'systemSerif'
  | 'georgia'
  | 'charter'
  | 'arial'
  | 'times'
  | 'inter'
  | 'lora'
  | 'merriweather'
  | 'sourceserif'
  | 'librebaskerville'
  | 'crimsonpro'
  | 'ebgaramond'
  | 'atkinson'
  | 'bitter'

export type ReaderAppearanceTheme =
  'original' | 'quiet' | 'paper' | 'bold' | 'calm' | 'focus' | 'custom'
export type ReaderColumnMode = 'auto' | 'single' | 'double'
export type ReaderTextAlign = 'left' | 'justify'

export interface UserReadingSettings {
  annotationMode: AnnotationMode
  bgColor: 'white' | 'cream' | 'green' | 'dark'
  theme: ReaderAppearanceTheme
  fontSize: number
  fontWeight: number
  lineHeight: number
  letterSpacing: number
  wordSpacing: number
  contentWidth: number
  columnMode: ReaderColumnMode
  textAlign: ReaderTextAlign
  readingBrightness: number
  customLayoutEnabled: boolean
  fontFamily: ReaderFont
  ttsSpeed: number
}

export const DEFAULT_SETTINGS: UserReadingSettings = {
  annotationMode: 'rhythm',
  bgColor: 'cream',
  theme: 'paper',
  fontSize: 20,
  fontWeight: 400,
  lineHeight: 1.72,
  letterSpacing: 0,
  wordSpacing: 0,
  contentWidth: 720,
  columnMode: 'single',
  textAlign: 'left',
  readingBrightness: 1,
  customLayoutEnabled: false,
  fontFamily: 'literary',
  ttsSpeed: 1.0,
}

export interface ComprehensionStatus {
  pageIndex: number
  status: 'mastered' | 'fuzzy' | 'review'
}

export interface AudioReaderState {
  isPlaying: boolean
  isPaused: boolean
  currentSentenceIndex: number
  currentPhraseIndex: number
  speed: number
}

export interface AIAgentState {
  isOpen: boolean
  loading: boolean
  selectedText: string
  sentenceId: string | null
  type: 'meaning' | 'hint' | 'translation' | 'grammar' | null
  result: ReaderAgentResult | null
}

export interface GrammarAnalysis {
  sentenceSkeleton: string
  meaningFlow: string[]
  chunkRelationships: { chunk: string; role: string }[]
  grammar: { topic: string; explanation: string }[]
  readingStrategy: string
  naturalTranslation: string
}

export interface QuickAssistance {
  naturalTranslation: string
  readingStrategy: string
  readingMove?: string
  whyReadThisWay?: string
  nextTimeNotice?: string
  meaningFirst?: string
  error?: string
}

export interface ContextualWordMeaning {
  meaningInContext: string
  contextClue: string
  sentenceTranslation: string
  dictionary: {
    headword: string
    definition: string
    sourceName: string
  } | null
  contextStatus?: 'pending' | 'ready' | 'unavailable'
}

export type ReaderAgentResult = GrammarAnalysis | QuickAssistance

export interface ImageGenerationState {
  isOpen: boolean
  loading: boolean
  prompt: string
  selectedText: string
  imageUrl: string
  keywords: string[]
  explanation: string
}

export interface ComprehensionSummary {
  mainIdea: string
  keySentence: string
  questions: string[]
  trickySentence: string
}

export interface SentenceChunkFromAPI {
  originalSentence: string
  chunks: string[]
}

export interface StructuredChapterFromAPI {
  stage: {
    id: string
    bookId: string
    chapterIndex: number
    stageTitle: string
    stageSummary: string
  }
  paragraphs: Array<{
    id: string
    paragraphIndex: number
    text: string
    sentenceCount: number
    estimatedLexile: number
    sentences: Array<{
      id: string
      sentenceIndex: number
      text: string
      chunks: Array<{
        id: string
        chunkIndex: number
        text: string
        role: string
        isCoreMeaning: boolean
      }>
    }>
  }>
}

export interface ChapterFromAPI {
  id: string
  chapterIndex: number
  displayIndex?: number
  sourceChapterIndex?: number
  content: string
  contentHtml?: string
  contentText?: string
  stageTitle?: string
  stageSummary?: string
  sourceHref?: string
  sourceFragment?: string
  sectionType?: string
  linear?: boolean
  tocLevel?: number
  tocParentId?: string
  tocLabel?: string
  schemaVersion?: number
}

export interface TocItemFromAPI {
  id: string
  label: string
  href?: string
  contentPath?: string
  fragment?: string
  level: number
  playOrder?: number
  children: TocItemFromAPI[]
  stageId?: string
  sourceHref?: string
}

export interface ReadingProgressFromAPI {
  userId: string
  bookId: string
  chapterId: string
  currentParagraphIndex: number
  completedParagraphs: number
  totalParagraphs: number
  totalReadingMs: number
  completedAt: string | null
  updatedAt: string
}
