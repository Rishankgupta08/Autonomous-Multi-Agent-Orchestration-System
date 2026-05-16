import { Link } from 'react-router-dom';
import { motion } from 'motion/react';
import ParticleBackground from '../components/background/ParticleBackground';
import LogoSymbol from '../components/common/LogoSymbol';
import Button from '../components/common/Button';

export default function NotFoundPage() {
  return (
    <div className="min-h-screen bg-[#080808] relative flex items-center justify-center px-6">
      <ParticleBackground nodeCount={30} velocity={0.2} maxDistance={100} />

      <motion.div
        className="relative z-10 text-center"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
      >
        <div className="text-[120px] font-extrabold text-[#1C1C1C] mb-8">404</div>

        <LogoSymbol size={32} className="text-[#333] mx-auto mb-6" />

        <h1 className="text-[24px] font-semibold text-[#F0F0F0] mb-3">Page not found</h1>
        <p className="text-[16px] text-[#555] max-w-md mx-auto mb-8">
          The page you're looking for doesn't exist or has been moved.
        </p>

        <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
          <Link to="/">
            <Button variant="primary">← Go Home</Button>
          </Link>
          <Link to="/workflow">
            <Button variant="ghost">Open Command Center</Button>
          </Link>
        </div>
      </motion.div>
    </div>
  );
}
