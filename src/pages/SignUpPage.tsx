import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import ParticleBackground from '../components/background/ParticleBackground';
import LogoSymbol from '../components/common/LogoSymbol';
import Input from '../components/common/Input';
import Button from '../components/common/Button';
import { scaleIn } from '../lib/animations';
import { useAuthStore } from '../store/authStore';
import { MOCK_USER } from '../lib/mockData';

export default function SignUpPage() {
  const navigate = useNavigate();
  const setUser = useAuthStore((state) => state.setUser);
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState({
    name: '',
    username: '',
    email: '',
    password: ''
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);

    // Simulate API call
    await new Promise(resolve => setTimeout(resolve, 1000));

    setUser(MOCK_USER, 'mock-token');
    navigate('/integrations');
  };

  const handleGitHubOAuth = async () => {
    setLoading(true);
    await new Promise(resolve => setTimeout(resolve, 1000));
    setUser(MOCK_USER, 'mock-token');
    navigate('/integrations');
  };

  return (
    <div className="min-h-screen bg-[#080808] relative flex items-center justify-center px-6">
      <ParticleBackground nodeCount={25} velocity={0.15} maxDistance={80} />

      <Link to="/" className="absolute top-9 left-1/2 -translate-x-1/2 z-20">
        <LogoSymbol size={24} className="text-[#888] hover:text-[#F0F0F0] transition-colors" />
      </Link>

      <motion.div
        className="w-full max-w-[480px] glass-card p-10 relative z-10"
        style={{ borderTop: '1px solid rgba(45,142,255,0.3)' }}
        variants={scaleIn}
        initial="hidden"
        animate="show"
      >
        <div className="text-center mb-6">
          <LogoSymbol size={24} className="text-[#888] mx-auto mb-4" />
          <div className="h-px bg-[rgba(255,255,255,0.06)] mb-6" />
          <h1 className="text-[28px] font-semibold text-[#F0F0F0] mb-2">Create your account</h1>
          <p className="text-[14px] text-[#555]">Join MOAE and automate your dev workflow.</p>
        </div>

        <div className="space-y-8">
          <Button
            variant="primary"
            fullWidth
            loading={loading}
            loadingText="Creating account..."
            onClick={handleGitHubOAuth}
            iconLeft={
              <svg className="w-[18px] h-[18px]" fill="currentColor" viewBox="0 0 24 24">
                <path fillRule="evenodd" d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z" clipRule="evenodd" />
              </svg>
            }
          >
            Continue with GitHub
          </Button>

          <div className="relative">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full h-px bg-[rgba(255,255,255,0.06)]" />
            </div>
            <div className="relative flex justify-center text-[12px]">
              <span className="px-3 bg-[#161616] text-[#333]">or sign up with email</span>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-3">
            <Input
              label="Full Name"
              placeholder="Your full name"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              required
            />

            <div className="relative">
              <Input
                label="GitHub Username"
                placeholder="your-github-handle"
                monoFont
                value={formData.username}
                onChange={(e) => setFormData({ ...formData, username: e.target.value })}
                className="pl-6"
                required
              />
              <span className="absolute left-3 top-[38px] text-[14px] text-[#555] font-mono">@</span>
            </div>

            <Input
              label="Email"
              type="email"
              placeholder="you@company.com"
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              required
            />

            <Input
              label="Password"
              type="password"
              placeholder="Choose a strong password"
              value={formData.password}
              onChange={(e) => setFormData({ ...formData, password: e.target.value })}
              required
            />

            <div className="pt-4">
              <Button type="submit" variant="primary" fullWidth loading={loading} loadingText="Creating account...">
                Create Account
              </Button>
            </div>
          </form>

          <p className="text-center text-[14px] text-[#555]">
            Already have an account?{' '}
            <Link to="/login" className="text-[#888] hover:text-[#F0F0F0] transition-colors">
              Log in
            </Link>
          </p>

          <p className="text-center text-[12px] text-[#333]">
            By continuing, you agree to our Terms & Privacy.
          </p>
        </div>
      </motion.div>
    </div>
  );
}
