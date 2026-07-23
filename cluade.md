# سجل الديون — Design System

## Stack
- Kotlin, XML layouts, minSdk 26, targetSdk 36
- Room with annotationProcessor (never change this)
- Material Design 3 (MDC-Android)
- No Jetpack Compose

## Colors (always hardcoded hex, never @color/ except black/white)
Background: #0D2117
Surface/cards: #132B1E
Primary accent: #CFFF04
Text primary: #FFFFFF
Text secondary: #6B9E7A
Warning: #FF8C00
Error: #FF4444
Divider: #1A3525
Button background: #CFFF04
Button text on bright button: #0D2117

## Typography
Amounts: 52sp+ bold textColor #CFFF04 with " د.ع" and thousands separator
Section numbers (stats): 40sp bold textColor #CFFF04
Headings: 24sp bold textColor #FFFFFF
Labels above numbers: 12sp textColor #6B9E7A
Body text: 15sp textColor #FFFFFF

## Components
Always MaterialCardView not CardView
Always MaterialButton not Button
Always TextInputLayout + TextInputEditText not plain EditText
Button height minimum: 56dp
Button cornerRadius: 12dp
Button backgroundTint: #CFFF04
Button textColor: #0D2117
Card cornerRadius: 16dp
Card elevation: 0dp
Card strokeWidth: 0dp (no borders, no shadows)
Card cardBackgroundColor: #132B1E

## Rules
- RTL: every layout root must have android:layoutDirection="rtl"
- Every screen background: #0D2117
- Never use الذمم or الذمة anywhere
- Never use @color/ except @color/black and @color/white
- All activities in com.sajilalduyun.app root package
- app:tint not android:tint on ImageView/ImageButton
- FileProvider authority: ${applicationId}.fileprovider
- Room uses annotationProcessor only, never ksp or kapt
- All money amounts in #CFFF04 electric lime — no exceptions
- No card borders, no shadows, surfaces are just slightly lighter green
- Whitespace is generous — 20dp screen padding, 16dp between cards
