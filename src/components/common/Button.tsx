import { ButtonHTMLAttributes, ReactNode } from 'react';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'ghost' | 'text' | 'blue' | 'purple' | 'green' | 'danger';
  loading?: boolean;
  loadingText?: string;
  fullWidth?: boolean;
  iconLeft?: ReactNode;
  iconRight?: ReactNode;
  children: ReactNode;
}

export default function Button({
  variant = 'primary',
  loading = false,
  loadingText,
  fullWidth = false,
  iconLeft,
  iconRight,
  children,
  className = '',
  disabled,
  ...props
}: ButtonProps) {
  const baseStyles = 'relative inline-flex items-center justify-center gap-2 font-medium transition-all duration-150 disabled:opacity-50 disabled:cursor-not-allowed';

  const sizeStyles = 'h-12 px-6 text-[15px] rounded-[10px]';

  const variantStyles = {
    primary: 'bg-[rgba(255,255,255,0.08)] border border-[rgba(255,255,255,0.18)] text-[#F0F0F0] hover:bg-[rgba(255,255,255,0.13)] hover:-translate-y-[1px] hover:shadow-[0_0_20px_rgba(255,255,255,0.06)] active:translate-y-0',
    ghost: 'bg-transparent border border-[rgba(255,255,255,0.08)] text-[#888] hover:text-[#F0F0F0] hover:border-[rgba(255,255,255,0.18)]',
    text: 'bg-transparent border-0 text-[#666] hover:underline',
    blue: 'bg-[rgba(255,255,255,0.08)] border border-[rgba(45,142,255,0.3)] text-[#F0F0F0] hover:bg-[rgba(255,255,255,0.13)] hover:shadow-[0_0_20px_rgba(45,142,255,0.15)]',
    purple: 'bg-[rgba(255,255,255,0.08)] border border-[rgba(124,58,237,0.3)] text-[#F0F0F0] hover:bg-[rgba(255,255,255,0.13)] hover:shadow-[0_0_20px_rgba(124,58,237,0.15)]',
    green: 'bg-[rgba(255,255,255,0.08)] border border-[rgba(34,197,94,0.3)] text-[#F0F0F0] hover:bg-[rgba(255,255,255,0.13)] hover:shadow-[0_0_20px_rgba(34,197,94,0.15)]',
    danger: 'bg-transparent border border-[rgba(239,68,68,0.25)] text-[#EF4444] hover:border-[rgba(239,68,68,0.5)] hover:shadow-[0_0_20px_rgba(239,68,68,0.1)]'
  };

  const widthStyles = fullWidth ? 'w-full' : '';

  return (
    <button
      className={`${baseStyles} ${sizeStyles} ${variantStyles[variant]} ${widthStyles} ${className}`}
      disabled={disabled || loading}
      aria-busy={loading}
      {...props}
    >
      {loading && (
        <svg
          className="animate-spin h-[18px] w-[18px]"
          xmlns="http://www.w3.org/2000/svg"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle
            className="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            strokeWidth="2"
          />
          <path
            className="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
          />
        </svg>
      )}
      {!loading && iconLeft}
      {loading ? loadingText || children : children}
      {!loading && iconRight}
    </button>
  );
}
