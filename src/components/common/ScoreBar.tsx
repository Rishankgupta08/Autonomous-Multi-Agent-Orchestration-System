import { useEffect, useRef, useState } from 'react';

interface ScoreBarProps {
  value: number;
  label: string;
  showValue?: boolean;
}

export default function ScoreBar({ value, label, showValue = true }: ScoreBarProps) {
  const [isVisible, setIsVisible] = useState(false);
  const [hasAnimated, setHasAnimated] = useState(false);
  const barRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (hasAnimated) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setIsVisible(true);
          setHasAnimated(true);
          observer.disconnect();
        }
      },
      { threshold: 0.1 }
    );

    if (barRef.current) {
      observer.observe(barRef.current);
    }

    return () => observer.disconnect();
  }, [hasAnimated]);

  const getColor = (val: number) => {
    if (val >= 90) return '#22C55E';
    if (val >= 70) return '#F59E0B';
    return '#EF4444';
  };

  const color = getColor(value);

  return (
    <div ref={barRef} className="w-full">
      <div className="flex items-center justify-between mb-2">
        <span className="text-[13px] text-[#888]">{label}</span>
        {showValue && (
          <span
            className="text-[13px] font-mono"
            style={{ color }}
          >
            {value}
          </span>
        )}
      </div>

      <div className="w-full h-1 bg-[rgba(255,255,255,0.06)] rounded-sm overflow-hidden">
        <div
          className="h-full rounded-sm transition-all duration-[800ms]"
          style={{
            width: isVisible ? `${value}%` : '0%',
            background: `linear-gradient(to right, ${color}, ${color}b3)`,
            transitionTimingFunction: 'cubic-bezier(0.16, 1, 0.3, 1)'
          }}
        />
      </div>
    </div>
  );
}
