interface LogoSymbolProps {
  size?: number;
  className?: string;
}

export default function LogoSymbol({ size = 24, className = "" }: LogoSymbolProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      className={className}
    >
      <rect x="9" y="1" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.9" />
      <rect x="17" y="4" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.7" transform="rotate(45 20 7)" />
      <rect x="18" y="9" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.6" transform="rotate(90 21 12)" />
      <rect x="15" y="17" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.7" transform="rotate(135 18 20)" />
      <rect x="9" y="18" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.9" />
      <rect x="2" y="17" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.7" transform="rotate(-135 5 20)" />
      <rect x="1" y="9" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.6" transform="rotate(-90 4 12)" />
      <rect x="3" y="4" width="6" height="6" rx="1.5" fill="currentColor" opacity="0.7" transform="rotate(-45 6 7)" />
    </svg>
  );
}
