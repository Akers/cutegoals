/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './admin.html', './parent.html', './child.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        cg: {
          bg: 'var(--cg-bg)',
          surface: 'var(--cg-surface)',
          'surface-raised': 'var(--cg-surface-raised)',
          primary: 'var(--cg-primary)',
          'primary-hover': 'var(--cg-primary-hover)',
          'primary-active': 'var(--cg-primary-active)',
          'primary-text': 'var(--cg-primary-text)',
          secondary: 'var(--cg-secondary)',
          'secondary-hover': 'var(--cg-secondary-hover)',
          accent: 'var(--cg-accent)',
          'accent-hover': 'var(--cg-accent-hover)',
          'accent-text': 'var(--cg-accent-text)',
          danger: 'var(--cg-danger)',
          'danger-hover': 'var(--cg-danger-hover)',
          'danger-text': 'var(--cg-danger-text)',
          success: 'var(--cg-success)',
          'success-bg': 'var(--cg-success-bg)',
          warning: 'var(--cg-warning)',
          'warning-bg': 'var(--cg-warning-bg)',
          info: 'var(--cg-info)',
          'info-bg': 'var(--cg-info-bg)',
          text: 'var(--cg-text)',
          'text-muted': 'var(--cg-text-muted)',
          border: 'var(--cg-border)',
          focus: 'var(--cg-focus)',
        },
      },
      fontFamily: {
        sans: ['var(--cg-font-sans)'],
        display: ['var(--cg-font-display)'],
      },
      borderRadius: {
        'cg-sm': 'var(--cg-radius-sm)',
        'cg-md': 'var(--cg-radius-md)',
        'cg-lg': 'var(--cg-radius-lg)',
      },
      boxShadow: {
        'cg-sm': 'var(--cg-shadow-sm)',
        'cg-md': 'var(--cg-shadow-md)',
        'cg-lg': 'var(--cg-shadow-lg)',
      },
      transitionDuration: {
        'cg-fast': 'var(--cg-transition-fast)',
        'cg-base': 'var(--cg-transition-base)',
      },
      minWidth: {
        'touch': 'var(--cg-touch-target)',
      },
      minHeight: {
        'touch': 'var(--cg-touch-target)',
      },
    },
  },
  plugins: [],
};
