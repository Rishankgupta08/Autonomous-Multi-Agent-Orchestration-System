import { Link } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import LogoSymbol from '../common/LogoSymbol';
import Button from '../common/Button';
import { useState } from 'react';
import { motion, AnimatePresence } from 'motion/react';

export default function Navbar() {
  const { isAuthenticated, user } = useAuthStore();
  const [showDropdown, setShowDropdown] = useState(false);

  return (
    <nav className="sticky top-0 z-50 h-14 md:h-14 border-b border-[rgba(255,255,255,0.05)]"
      style={{
        background: 'rgba(8,8,8,0.75)',
        backdropFilter: 'blur(20px) saturate(150%)',
        WebkitBackdropFilter: 'blur(20px) saturate(150%)'
      }}
    >
      <div className="max-w-[1400px] mx-auto h-full px-6 flex items-center justify-between">
        {/* Left side */}
        <Link to="/" className="flex items-center gap-2">
          <LogoSymbol size={20} className="text-white hidden md:block" />
          <span className="text-white font-bold text-[15px] tracking-[-0.04em]">MOAE</span>
          <span className="hidden md:block text-[#333]">·</span>
          <span className="hidden md:flex items-center gap-2 px-2 py-0.5 rounded-full text-[11px] text-[#666] bg-[rgba(255,255,255,0.05)] border border-[rgba(255,255,255,0.08)] font-medium">
            Beta
          </span>
        </Link>

        {/* Right side */}
        <div className="flex items-center gap-3">
          {!isAuthenticated ? (
            <>
              <Link to="/signup">
                <Button variant="ghost" className="!h-9 !px-4 !text-[14px]">
                  Sign Up
                </Button>
              </Link>
              <Link to="/login">
                <Button variant="ghost" className="!h-9 !px-4 !text-[14px]">
                  Log In
                </Button>
              </Link>
            </>
          ) : (
            <div className="relative">
              <button
                onClick={() => setShowDropdown(!showDropdown)}
                className="relative w-8 h-8 rounded-full overflow-hidden border-2 border-[rgba(255,255,255,0.1)] hover:border-[rgba(255,255,255,0.2)] transition-colors"
              >
                <img src={user?.avatarUrl} alt={user?.name} className="w-full h-full object-cover" />
                <span className="absolute bottom-0 right-0 w-2 h-2 bg-[#22C55E] rounded-full border-2 border-[#080808]" />
              </button>

              <AnimatePresence>
                {showDropdown && (
                  <>
                    <div
                      className="fixed inset-0 z-40"
                      onClick={() => setShowDropdown(false)}
                    />
                    <motion.div
                      initial={{ opacity: 0, scale: 0.95, y: -8 }}
                      animate={{ opacity: 1, scale: 1, y: 0 }}
                      exit={{ opacity: 0, scale: 0.95, y: -8 }}
                      transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
                      className="absolute right-0 top-12 w-[220px] glass-card !rounded-xl p-2 z-50"
                      style={{ transformOrigin: 'top right' }}
                    >
                      <div className="px-3 py-2 border-b border-[rgba(255,255,255,0.06)]">
                        <div className="flex items-center gap-3">
                          <img src={user?.avatarUrl} alt={user?.name} className="w-10 h-10 rounded-full" />
                          <div className="flex-1 min-w-0">
                            <p className="text-[14px] font-semibold text-[#F0F0F0] truncate">{user?.name}</p>
                            <p className="text-[12px] text-[#555] font-mono truncate">@{user?.username}</p>
                          </div>
                        </div>
                      </div>

                      <div className="py-1">
                        <Link
                          to="/profile"
                          className="block px-3 py-2 text-[14px] text-[#888] hover:bg-[rgba(255,255,255,0.04)] rounded-lg transition-colors"
                          onClick={() => setShowDropdown(false)}
                        >
                          Profile
                        </Link>
                        <Link
                          to="/workflows"
                          className="block px-3 py-2 text-[14px] text-[#888] hover:bg-[rgba(255,255,255,0.04)] rounded-lg transition-colors"
                          onClick={() => setShowDropdown(false)}
                        >
                          Previous Workflows
                        </Link>
                      </div>

                      <div className="border-t border-[rgba(255,255,255,0.06)] pt-1">
                        <button
                          onClick={() => {
                            useAuthStore.getState().logout();
                            setShowDropdown(false);
                          }}
                          className="w-full text-left px-3 py-2 text-[14px] text-[#888] hover:text-[#EF4444] hover:bg-[rgba(255,255,255,0.04)] rounded-lg transition-colors"
                        >
                          Log Out
                        </button>
                      </div>
                    </motion.div>
                  </>
                )}
              </AnimatePresence>
            </div>
          )}
        </div>
      </div>
    </nav>
  );
}
