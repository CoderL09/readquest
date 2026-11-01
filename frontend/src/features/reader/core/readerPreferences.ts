import type { CSSProperties } from 'vue'
import type { ReaderAppearanceTheme, ReaderFont, UserReadingSettings } from './types'
import { DEFAULT_SETTINGS } from './types'

export type ThemePreset = {
  key: Exclude<ReaderAppearanceTheme, 'custom'>
  label: string
  background: string
  text: string
  muted: string
  card: string
  border: string
  fontWeight?: number
  lineHeight?: number
  fontSize?: number
  letterSpacing?: number
  wordSpacing?: number
  dimChrome?: boolean
}

export const THEME_PRESETS: ThemePreset[] = [
  {
    key: 'original',
    label: '原始',
    background: '#FFFFFF',
    text: '#171717',
    muted: '#707070',
    card: '#FFFFFF',
    border: '#E7E3DB',
    lineHeight: 1.65,
    fontSize: 19,
    letterSpacing: 0,
    wordSpacing: 0.04,
  },
  {
    key: 'quiet',
    label: '安静',
    background: '#4A494F',
    text: '#E9E7E2',
    muted: '#BCB8B0',
    card: '#545258',
    border: '#68666D',
    lineHeight: 1.78,
    fontSize: 20,
    letterSpacing: 0.02,
    wordSpacing: 0.06,
  },
  {
    key: 'paper',
    label: '纸张',
    background: '#F3F1EC',
    text: '#292622',
    muted: '#78716A',
    card: '#FBFAF7',
    border: '#E2DED4',
    lineHeight: 1.72,
    fontSize: 20,
    letterSpacing: 0,
    wordSpacing: 0.03,
  },
  {
    key: 'bold',
    label: '粗体',
    background: '#FFFFFF',
    text: '#111111',
    muted: '#595959',
    card: '#FFFFFF',
    border: '#DDDAD2',
    fontWeight: 560,
    lineHeight: 1.7,
    fontSize: 19,
    letterSpacing: -0.005,
    wordSpacing: 0.04,
  },
  {
    key: 'calm',
    label: '平静',
    background: '#EFE1C2',
    text: '#34291F',
    muted: '#806E59',
    card: '#F7EBCF',
    border: '#DDCBA8',
    lineHeight: 1.8,
    fontSize: 20,
    letterSpacing: 0.01,
    wordSpacing: 0.06,
  },
  {
    key: 'focus',
    label: '专注',
    background: '#FAF8F2',
    text: '#171717',
    muted: '#8A8478',
    card: '#FFFEFA',
    border: '#E7E2D7',
    lineHeight: 1.88,
    fontSize: 21,
    letterSpacing: 0.015,
    wordSpacing: 0.08,
    dimChrome: true,
  },
]

export const FONT_OPTIONS: Array<{ key: ReaderFont; label: string; family: string }> = [
  {
    key: 'literary',
    label: 'Literary Serif',
    family: '"Iowan Old Style", "Palatino Linotype", Palatino, Georgia, serif',
  },
  {
    key: 'systemSerif',
    label: 'System Serif',
    family: 'ui-serif, Georgia, Cambria, "Times New Roman", Times, serif',
  },
  { key: 'georgia', label: 'Georgia', family: 'Georgia, "Times New Roman", serif' },
  {
    key: 'charter',
    label: 'Charter',
    family: 'Charter, "Bitstream Charter", "Iowan Old Style", Georgia, serif',
  },
  { key: 'inter', label: 'Inter', family: '"Inter", ui-sans-serif, system-ui, sans-serif' },
  { key: 'lora', label: 'Lora', family: '"Lora", Georgia, "Times New Roman", serif' },
  { key: 'merriweather', label: 'Merriweather', family: '"Merriweather", Georgia, serif' },
  {
    key: 'sourceserif',
    label: 'Source Serif 4',
    family: '"Source Serif 4", Georgia, "Times New Roman", serif',
  },
  {
    key: 'librebaskerville',
    label: 'Libre Baskerville',
    family: '"Libre Baskerville", Georgia, serif',
  },
  { key: 'crimsonpro', label: 'Crimson Pro', family: '"Crimson Pro", Georgia, serif' },
  { key: 'ebgaramond', label: 'EB Garamond', family: '"EB Garamond", Georgia, serif' },
  {
    key: 'atkinson',
    label: 'Atkinson Hyperlegible',
    family: '"Atkinson Hyperlegible", Arial, sans-serif',
  },
  { key: 'bitter', label: 'Bitter', family: '"Bitter", Georgia, serif' },
  {
    key: 'systemSans',
    label: 'System Sans',
    family:
      'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
  },
  { key: 'arial', label: 'Arial', family: 'Arial, Helvetica, sans-serif' },
  { key: 'times', label: 'Times New Roman', family: '"Times New Roman", Times, serif' },
]

export function clampNumber(value: unknown, min: number, max: number, fallback: number): number {
  const numeric = typeof value === 'number' && Number.isFinite(value) ? value : fallback
  return Math.min(max, Math.max(min, numeric))
}

export function getThemePreset(theme: ReaderAppearanceTheme): ThemePreset {
  return (
    THEME_PRESETS.find((preset) => preset.key === theme) ??
    THEME_PRESETS.find((preset) => preset.key === DEFAULT_SETTINGS.theme)!
  )
}

export function themeToLegacyBg(theme: ReaderAppearanceTheme): UserReadingSettings['bgColor'] {
  if (theme === 'quiet') return 'dark'
  if (theme === 'original' || theme === 'bold') return 'white'
  return 'cream'
}

export function applyThemePreset(
  settings: UserReadingSettings,
  theme: Exclude<ReaderAppearanceTheme, 'custom'>,
): UserReadingSettings {
  const preset = getThemePreset(theme)
  return {
    ...settings,
    theme,
    bgColor: themeToLegacyBg(theme),
    fontWeight: preset.fontWeight ?? DEFAULT_SETTINGS.fontWeight,
    lineHeight: preset.lineHeight ?? DEFAULT_SETTINGS.lineHeight,
    fontSize: preset.fontSize ?? DEFAULT_SETTINGS.fontSize,
    letterSpacing: preset.letterSpacing ?? DEFAULT_SETTINGS.letterSpacing,
    wordSpacing: preset.wordSpacing ?? DEFAULT_SETTINGS.wordSpacing,
  }
}

export function resetReaderPreferences(
  current?: Partial<UserReadingSettings>,
): UserReadingSettings {
  return {
    ...DEFAULT_SETTINGS,
    annotationMode: current?.annotationMode ?? DEFAULT_SETTINGS.annotationMode,
    ttsSpeed: current?.ttsSpeed ?? DEFAULT_SETTINGS.ttsSpeed,
  }
}

export function getFontFamily(font: ReaderFont): string {
  return FONT_OPTIONS.find((option) => option.key === font)?.family ?? FONT_OPTIONS[0].family
}

export type ReaderCssVars = CSSProperties & {
  '--reader-bg': string
  '--reader-text': string
  '--reader-muted': string
  '--reader-card': string
  '--reader-border': string
  '--reader-font-family': string
  '--reader-font-size': string
  '--reader-font-weight': number
  '--reader-line-height': number
  '--reader-letter-spacing': string
  '--reader-word-spacing': string
  '--reader-content-width': string
  '--reader-page-padding': string
  '--reader-brightness': number
}

export function getReaderCssVars(settings: UserReadingSettings): ReaderCssVars {
  const preset = getThemePreset(settings.theme)
  const brightness = clampNumber(
    settings.readingBrightness,
    0.72,
    1.18,
    DEFAULT_SETTINGS.readingBrightness,
  )
  const contentWidth = clampNumber(settings.contentWidth, 560, 900, DEFAULT_SETTINGS.contentWidth)
  const pagePadding = Math.round(Math.max(20, Math.min(56, (contentWidth - 520) / 8 + 20)))

  return {
    '--reader-bg': preset.background,
    '--reader-text': preset.text,
    '--reader-muted': preset.muted,
    '--reader-card': preset.card,
    '--reader-border': preset.border,
    '--reader-font-family': getFontFamily(settings.fontFamily),
    '--reader-font-size': `${clampNumber(settings.fontSize, 14, 30, DEFAULT_SETTINGS.fontSize)}px`,
    '--reader-font-weight': clampNumber(settings.fontWeight, 360, 680, DEFAULT_SETTINGS.fontWeight),
    '--reader-line-height': clampNumber(settings.lineHeight, 1.3, 2.2, DEFAULT_SETTINGS.lineHeight),
    '--reader-letter-spacing': `${clampNumber(settings.letterSpacing, -0.02, 0.12, DEFAULT_SETTINGS.letterSpacing)}em`,
    '--reader-word-spacing': `${clampNumber(settings.wordSpacing, 0, 0.3, DEFAULT_SETTINGS.wordSpacing)}em`,
    '--reader-content-width': `${contentWidth}px`,
    '--reader-page-padding': `${pagePadding}px`,
    '--reader-brightness': brightness,
  }
}

export function sanitizeReaderSettings(saved: Partial<UserReadingSettings>): UserReadingSettings {
  const theme = THEME_PRESETS.some((preset) => preset.key === saved.theme)
    ? (saved.theme as UserReadingSettings['theme'])
    : DEFAULT_SETTINGS.theme
  const fontFamily =
    FONT_OPTIONS.some((option) => option.key === saved.fontFamily) ||
    ['serif', 'sans', 'humanist', 'mono'].includes(saved.fontFamily ?? '')
      ? (saved.fontFamily as ReaderFont)
      : DEFAULT_SETTINGS.fontFamily
  const customLayoutEnabled = Boolean(saved.customLayoutEnabled)
  const columnMode = customLayoutEnabled && saved.columnMode === 'double' ? 'double' : 'single'

  return {
    ...DEFAULT_SETTINGS,
    ...saved,
    theme,
    bgColor: saved.bgColor ?? themeToLegacyBg(theme),
    fontFamily,
    fontSize: clampNumber(
      saved.fontSize,
      14,
      30,
      getThemePreset(theme).fontSize ?? DEFAULT_SETTINGS.fontSize,
    ),
    fontWeight: clampNumber(
      saved.fontWeight,
      360,
      680,
      getThemePreset(theme).fontWeight ?? DEFAULT_SETTINGS.fontWeight,
    ),
    lineHeight: clampNumber(
      saved.lineHeight,
      1.3,
      2.2,
      getThemePreset(theme).lineHeight ?? DEFAULT_SETTINGS.lineHeight,
    ),
    letterSpacing: clampNumber(
      saved.letterSpacing,
      -0.02,
      0.12,
      getThemePreset(theme).letterSpacing ?? DEFAULT_SETTINGS.letterSpacing,
    ),
    wordSpacing: clampNumber(
      saved.wordSpacing,
      0,
      0.3,
      getThemePreset(theme).wordSpacing ?? DEFAULT_SETTINGS.wordSpacing,
    ),
    contentWidth: clampNumber(saved.contentWidth, 560, 900, DEFAULT_SETTINGS.contentWidth),
    columnMode,
    textAlign: saved.textAlign === 'justify' ? 'justify' : 'left',
    readingBrightness: clampNumber(
      saved.readingBrightness,
      0.72,
      1.18,
      DEFAULT_SETTINGS.readingBrightness,
    ),
    customLayoutEnabled,
  }
}
