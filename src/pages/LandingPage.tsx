import { motion } from 'motion/react';
import { Link } from 'react-router-dom';
import ParticleBackground from '../components/background/ParticleBackground';
import Navbar from '../components/layout/Navbar';
import Button from '../components/common/Button';
import { fadeUp, staggerContainer } from '../lib/animations';

export default function LandingPage() {
  return (
    <div className="min-h-screen relative" style={{ background: '#080808' }}>
      <ParticleBackground nodeCount={40} velocity={0.3} maxDistance={120} />

      <div className="relative z-10">
        <Navbar />

        {/* Hero Section */}
        <motion.div
          className="flex flex-col items-center justify-center px-6"
          style={{
            minHeight: 'calc(100vh - 56px)',
            background: 'radial-gradient(ellipse 60% 40% at 50% 60%, rgba(45, 142, 255, 0.04) 0%, transparent 70%)'
          }}
          variants={staggerContainer(0.1)}
          initial="hidden"
          animate="show"
        >
          <div className="max-w-[720px] mx-auto text-center space-y-8">
            {/* Eyebrow */}
            <motion.div variants={fadeUp} className="inline-block">
              <div
                className="inline-flex items-center px-3.5 py-1.5 rounded-full font-mono text-[11px] tracking-[0.15em]"
                style={{
                  background: 'rgba(45,142,255,0.06)',
                  border: '1px solid rgba(45,142,255,0.2)',
                  color: 'rgba(45,142,255,0.8)'
                }}
              >
                AUTONOMOUS · AI · ORCHESTRATION
              </div>
            </motion.div>

            {/* Main Title */}
            <motion.h1
              className="font-extrabold text-white tracking-[-0.04em]"
              style={{ fontSize: 'clamp(72px, 12vw, 96px)' }}
              variants={fadeUp}
            >
              MOAE
            </motion.h1>

            {/* Subtitle */}
            <motion.p
              className="text-[18px] text-[#666] tracking-[0.04em]"
              variants={fadeUp}
            >
              Autonomous Multi-Agent Orchestration Engine
            </motion.p>

            {/* Description */}
            <motion.p
              className="text-[16px] text-[#555] max-w-[440px] mx-auto leading-[1.7]"
              variants={fadeUp}
            >
             One command. Every tool in sync. Intelligent execution from code to communication.
            </motion.p>

            {/* Feature Chips */}
            <motion.div
              className="flex flex-wrap items-center justify-center gap-3"
              variants={staggerContainer(0.06)}
            >
              {[
                { icon: '⬡', label: 'GitHub Automation', color: 'rgba(34,197,94,0.2)', bg: 'rgba(34,197,94,0.04)' },
                { icon: '◆', label: 'Jira Sync', color: 'rgba(45,142,255,0.2)', bg: 'rgba(45,142,255,0.04)' },
                { icon: '#', label: 'Slack Alerts', color: 'rgba(124,58,237,0.2)', bg: 'rgba(124,58,237,0.04)' }
              ].map((chip, i) => (
                <motion.div
                  key={i}
                  variants={fadeUp}
                  className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-[12px] font-mono"
                  style={{
                    border: `1px solid ${chip.color}`,
                    background: chip.bg
                  }}
                >
                  <span>{chip.icon}</span>
                  <span className="text-[#888]">{chip.label}</span>
                </motion.div>
              ))}
            </motion.div>

            {/* CTA Group */}
            <motion.div
              className="flex flex-col sm:flex-row items-center justify-center gap-3"
              variants={fadeUp}
            >
              <Link to="/login">
                <Button>→ Start New Workflow</Button>
              </Link>
              <Link to="/signup">
                <Button variant="text">Sign Up Free</Button>
              </Link>
            </motion.div>

            {/* Scroll Indicator */}
            <motion.div
              variants={fadeUp}
              className="flex justify-center pt-8"
            >
              <div
                className="w-[1px] h-8 animate-pulse"
                style={{
                  background: 'linear-gradient(to bottom, rgba(255,255,255,0.3), transparent)'
                }}
              />
            </motion.div>
          </div>
        </motion.div>

        {/* How MOAE Works Section */}
        <motion.div
          className="max-w-[720px] mx-auto px-6 py-16"
          initial="hidden"
          whileInView="show"
          viewport={{ once: true, margin: "-100px" }}
          variants={staggerContainer(0.1)}
        >
          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 md:gap-12">
            {[
              { num: '01', title: 'Command', desc: 'Type your prompt.' },
              { num: '02', title: 'Automate', desc: 'GitHub, Jira, Slack handled in seconds.' },
              { num: '03', title: 'Deliver', desc: 'Get results instantly.' }
            ].map((step, i) => (
              <motion.div
                key={i}
                variants={fadeUp}
                className="text-center md:text-left"
              >
                <p className="text-[11px] font-mono text-[#444] tracking-[0.15em] mb-2">{step.num}</p>
                <h3 className="text-[20px] font-semibold text-[#F0F0F0] mb-1">{step.title}</h3>
                <p className="text-[14px] text-[#555]">{step.desc}</p>
              </motion.div>
            ))}
          </div>
        </motion.div>
      </div>
    </div>
  );
}
