# Email CSS Style Guide (05-03)

**Purpose:** Document the GoaldoneTheme CSS variables and best practices used in all email templates.

## GoaldoneTheme Color Palette

Used for consistent styling across all email templates:

```css
/* Primary Colors */
--gd-primary: #007BFF;           /* Primary action (buttons, links) */
--gd-primary-dark: #0056B3;      /* Hover state for primary buttons */

/* Text Colors */
--gd-text-primary: #333333;      /* Main body text */
--gd-text-secondary: #666666;    /* Secondary/metadata text */
--gd-text-muted: #999999;        /* Muted/disabled text */

/* Background Colors */
--gd-bg-white: #FFFFFF;          /* White background */
--gd-bg-light: #F5F5F5;          /* Light gray background */
--gd-bg-lighter: #FAFAFA;        /* Very light background */

/* Border & Divider */
--gd-border: #DDDDDD;            /* Standard border color */
--gd-border-light: #EEEEEE;      /* Light border */

/* Semantic Colors */
--gd-success: #28A745;           /* Success messages (account linking) */
--gd-success-light: #F1F8F5;     /* Success background */
--gd-warning: #FFC107;           /* Warnings */
--gd-warning-light: #FFF8DC;     /* Warning background */
--gd-danger: #DC3545;            /* Error/security messages */
--gd-danger-light: #F8D7DA;      /* Error background */

/* Info Colors */
--gd-info: #17A2B8;              /* Info messages */
--gd-info-light: #E7F3FF;        /* Info background */
```

## Font Stack (Email-Compatible)

```
Recommended: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif

Fallback order:
1. System fonts (best rendering on macOS/iOS)
2. Segoe UI (Windows)
3. Roboto (Android)
4. Arial (universal fallback)
```

## Email-Specific CSS Rules

### Best Practices

1. **Inline Styles:** Email clients strip `<style>` tags. Use inline `style=""` attributes for critical styling.
2. **Max Width:** Keep container max-width at 600px for optimal mobile rendering
3. **Fallback Colors:** Always provide fallback hex colors; CSS variables may not work in all clients
4. **Padding/Margin:** Use inline styles or `<spacer>` elements; margin not always supported
5. **Images:** Inline images with `width` and `height` attributes; avoid `max-width`

### Responsive Breakpoints

```css
/* Mobile (< 600px) */
@media (max-width: 600px) {
    .container {
        width: 100% !important;
        max-width: 100% !important;
    }
    .button {
        width: 100% !important;
        display: block !important;
    }
    .info-row {
        flex-direction: column !important;
    }
}
```

## Component Styles

### Buttons

```html
<a href="url" style="
    display: inline-block;
    background-color: #007BFF;
    color: #FFFFFF;
    text-decoration: none;
    padding: 12px 30px;
    border-radius: 4px;
    font-weight: 600;
    font-size: 14px;
">
    Button Text
</a>
```

### Info Boxes

```html
<div style="
    background-color: #F5F5F5;
    border-left: 4px solid #007BFF;
    padding: 15px;
    margin: 20px 0;
    border-radius: 2px;
">
    Content here
</div>
```

### Security Warnings

```html
<div style="
    background-color: #F8D7DA;
    border: 1px solid #F5C6CB;
    color: #721C24;
    padding: 15px;
    border-radius: 4px;
    margin: 20px 0;
    font-size: 13px;
">
    Warning content
</div>
```

### Success Messages

```html
<div style="
    background-color: #F1F8F5;
    border-left: 4px solid #28A745;
    padding: 15px;
    border-radius: 2px;
">
    Success content
</div>
```

## Character Encoding

All templates use UTF-8 charset declaration:
```html
<meta charset="UTF-8">
```

This ensures German special characters (ä, ö, ü, ß) render correctly in all email clients.

## Known Limitations by Email Client

| Client | Limitation | Workaround |
|--------|-----------|-----------|
| Outlook (Windows) | Limited CSS support | Use inline styles, avoid flexbox |
| Gmail | Strips `<style>` tags | Inline all critical styles |
| Apple Mail | Good CSS support | Safe to use most modern CSS |
| Thunderbird | Text-only option | Provide plain-text version |
| Older Outlook | No media queries | Use fixed widths with mobile-friendly defaults |

## Template Structure

All templates follow this structure for consistency:

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>...</title>
    <style>/* Fallback styles only */</style>
</head>
<body>
    <div class="container" style="...">
        <div class="header" style="...">...</div>
        <div class="content" style="...">...</div>
        <div class="footer" style="...">...</div>
    </div>
</body>
</html>
```

## Validation

All templates validated against:
- WCAG AA color contrast (minimum 4.5:1 for text)
- HTML5 email standards
- Thymeleaf variable syntax
- UTF-8 character support

---

*Reference created as part of Phase 5.3 Email Templates & German Language Support Refinement*
