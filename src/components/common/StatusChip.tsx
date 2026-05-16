import { Check, X } from 'lucide-react';

interface StatusChipProps {
  variant: 'connected-green' | 'connected-blue' | 'connected-purple' | 'disconnected' | 'error';
  label?: string;
}

export default function StatusChip({ variant, label }: StatusChipProps) {
  const configs = {
    'connected-green': {
      bg: 'rgba(34,197,94,0.08)',
      border: 'rgba(34,197,94,0.2)',
      text: '#22C55E',
      label: label || 'Connected',
      showDot: true,
      showIcon: false
    },
    'connected-blue': {
      bg: 'rgba(45,142,255,0.08)',
      border: 'rgba(45,142,255,0.2)',
      text: '#2D8EFF',
      label: label || 'Connected',
      showDot: true,
      showIcon: false
    },
    'connected-purple': {
      bg: 'rgba(124,58,237,0.08)',
      border: 'rgba(124,58,237,0.2)',
      text: '#7C3AED',
      label: label || 'Connected',
      showDot: true,
      showIcon: false
    },
    disconnected: {
      bg: 'rgba(255,255,255,0.04)',
      border: 'rgba(255,255,255,0.08)',
      text: '#555',
      label: label || 'Not connected',
      showDot: true,
      showIcon: false
    },
    error: {
      bg: 'rgba(239,68,68,0.08)',
      border: 'rgba(239,68,68,0.2)',
      text: '#EF4444',
      label: label || 'Failed',
      showDot: false,
      showIcon: true
    }
  };

  const config = configs[variant];

  return (
    <div
      className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-[13px] font-medium"
      style={{
        background: config.bg,
        border: `1px solid ${config.border}`,
        color: config.text
      }}
    >
      {config.showDot && (
        <span
          className="w-1.5 h-1.5 rounded-full"
          style={{
            background: config.text,
            animation: variant.startsWith('connected') ? 'dotPulse 2s ease infinite' : 'none'
          }}
        />
      )}
      {config.showIcon && <X size={10} />}
      {config.label}
    </div>
  );
}
