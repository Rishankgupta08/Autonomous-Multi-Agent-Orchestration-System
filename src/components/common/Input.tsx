import { InputHTMLAttributes, ReactNode, useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helper?: string;
  monoFont?: boolean;
}

export default function Input({
  label,
  error,
  helper,
  monoFont = false,
  type = 'text',
  className = '',
  ...props
}: InputProps) {
  const [showPassword, setShowPassword] = useState(false);
  const isPassword = type === 'password';
  const inputType = isPassword && showPassword ? 'text' : type;

  return (
    <div className="w-full">
      {label && (
        <label className="block text-[14px] text-[#888] mb-2 font-normal">
          {label}
        </label>
      )}

      <div className="relative">
        <input
          type={inputType}
          className={`
            w-full h-11 px-3 rounded-[10px]
            bg-[rgba(255,255,255,0.03)]
            border border-[rgba(255,255,255,0.08)]
            text-[#F0F0F0] text-[14px]
            placeholder:text-[#444]
            focus:border-[rgba(255,255,255,0.22)]
            focus:shadow-[0_0_0_3px_rgba(255,255,255,0.04)]
            focus:outline-none
            transition-all duration-150
            ${error ? 'border-[rgba(239,68,68,0.5)] focus:shadow-[0_0_0_3px_rgba(239,68,68,0.08)]' : ''}
            ${monoFont ? 'font-mono' : ''}
            ${isPassword ? 'pr-10' : ''}
            ${className}
          `}
          {...props}
        />

        {isPassword && (
          <button
            type="button"
            onClick={() => setShowPassword(!showPassword)}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-[#444] hover:text-[#888] transition-colors"
            tabIndex={-1}
          >
            {showPassword ? (
              <EyeOff size={16} />
            ) : (
              <Eye size={16} />
            )}
          </button>
        )}
      </div>

      {error && (
        <p className="mt-1 text-[12px] text-[#EF4444]">{error}</p>
      )}

      {helper && !error && (
        <p className="mt-1 text-[12px] text-[#555]">{helper}</p>
      )}
    </div>
  );
}
