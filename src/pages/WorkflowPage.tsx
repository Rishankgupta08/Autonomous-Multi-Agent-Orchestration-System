import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { useAuthStore } from '../store/authStore';
import { useWorkflowStore } from '../store/workflowStore';
import LogoSymbol from '../components/common/LogoSymbol';
import Button from '../components/common/Button';
import ScoreBar from '../components/common/ScoreBar';
import { fadeUp, fadeUpLarge } from '../lib/animations';
import { Check, Loader2, X } from 'lucide-react';

const DEMO_STEPS = [
  { id: 's1', service: 'github' as const, name: 'Initializing Connection', description: 'Connecting to github.com/rishankgupta/repo', completionDetail: 'Repository accessed · moae-demo ↗' },
  { id: 's2', service: 'github' as const, name: 'Branch Operations', description: 'Creating branch: feature/ai-generated-slug', completionDetail: '✓ Branch created → feature/auth-login ↗' },
  { id: 's3', service: 'github' as const, name: 'Pull Request', description: 'Generating AI-written PR description', completionDetail: '✓ PR #47 opened → Add authentication feature ↗' },
  { id: 's4', service: 'jira' as const, name: 'Ticket Update', description: 'Updating ticket status and linking PR', completionDetail: '✓ JIR-420 → In Review, PR linked ↗' },
  { id: 's5', service: 'slack' as const, name: 'Team Notification', description: 'Composing and sending message to #dev-team', completionDetail: '✓ Message sent to #dev-team ↗' }
];

const SERVICE_COLORS = {
  github: { border: '#22C55E', bg: 'rgba(34,197,94,0.08)' },
  jira: { border: '#2D8EFF', bg: 'rgba(45,142,255,0.08)' },
  slack: { border: '#7C3AED', bg: 'rgba(124,58,237,0.08)' }
};

export default function WorkflowPage() {
  const { user } = useAuthStore();
  const [phase, setPhase] = useState<'input' | 'executing' | 'complete'>('input');
  const [command, setCommand] = useState('');
  const [steps, setSteps] = useState(DEMO_STEPS.map(s => ({ ...s, status: 'pending' as const })));
  const [currentStepIndex, setCurrentStepIndex] = useState(-1);

  const executeWorkflow = async () => {
    setPhase('executing');
    setSteps(DEMO_STEPS.map(s => ({ ...s, status: 'pending' as const })));

    for (let i = 0; i < DEMO_STEPS.length; i++) {
      setCurrentStepIndex(i);

      // Set step to active
      setSteps(prev => prev.map((s, idx) =>
        idx === i ? { ...s, status: 'active' as const } : s
      ));

      // Simulate execution
      await new Promise(resolve => setTimeout(resolve, 1200 + Math.random() * 800));

      // Set step to complete
      setSteps(prev => prev.map((s, idx) =>
        idx === i ? { ...s, status: 'complete' as const } : s
      ));

      await new Promise(resolve => setTimeout(resolve, 200));
    }

    // All complete
    await new Promise(resolve => setTimeout(resolve, 600));
    setPhase('complete');
  };

  const resetWorkflow = () => {
    setPhase('input');
    setCommand('');
    setSteps(DEMO_STEPS.map(s => ({ ...s, status: 'pending' as const })));
    setCurrentStepIndex(-1);
  };

  return (
    <div className="min-h-screen bg-[#080808]">
      {/* Top Bar */}
      <div
        className="sticky top-0 z-40 h-14 border-b border-[rgba(255,255,255,0.05)]"
        style={{
          background: 'rgba(8,8,8,0.85)',
          backdropFilter: 'blur(16px)',
          WebkitBackdropFilter: 'blur(16px)'
        }}
      >
        <div className="h-full px-6 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-white font-bold text-[15px]">MOAE</span>
            <span className="text-[#333]">/</span>
            <span className="text-[14px] text-[#555]">Command Center</span>
          </div>
          <button className="relative w-8 h-8 rounded-full overflow-hidden border-2 border-[rgba(255,255,255,0.1)]">
            <img src={user?.avatarUrl} alt={user?.name} className="w-full h-full object-cover" />
            <span className="absolute bottom-0 right-0 w-2 h-2 bg-[#22C55E] rounded-full border-2 border-[#080808]" />
          </button>
        </div>
      </div>

      {/* Main Content */}
      <div className="px-6 py-12">
        <AnimatePresence mode="wait">
          {phase === 'input' && (
            <motion.div
              key="input"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="max-w-[720px] mx-auto"
              style={{ minHeight: 'calc(100vh - 200px)', display: 'flex', flexDirection: 'column', justifyContent: 'center' }}
            >
              <h2 className="text-[28px] font-semibold text-[#F0F0F0] text-center mb-8">
                What would you like to automate?
              </h2>

              <div className="glass-card p-5 mb-4">
                <textarea
                  value={command}
                  onChange={(e) => setCommand(e.target.value)}
                  placeholder="Create a PR for the auth feature, update Jira JIR-420, and notify #dev-team on Slack..."
                  className="w-full min-h-[160px] bg-transparent border-0 text-[16px] text-[#F0F0F0] placeholder:text-[#444] resize-none focus:outline-none leading-[1.7]"
                  style={{ fontFamily: 'var(--font-sans)' }}
                />

                <div className="border-t border-[rgba(255,255,255,0.06)] mt-4 pt-3 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="h-7 px-2 glass-card rounded-lg flex items-center gap-1.5 text-[12px] text-[#555]">
                      <svg className="w-3 h-3" style={{ color: '#22C55E' }} fill="currentColor" viewBox="0 0 24 24">
                        <path fillRule="evenodd" d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z" clipRule="evenodd" />
                      </svg>
                      main
                    </div>
                  </div>
                  <span className="text-[12px] font-mono text-[#444]">{command.length} / 500</span>
                </div>
              </div>

              <Button
                variant="primary"
                className="!h-[52px] mx-auto"
                disabled={!command.trim()}
                onClick={executeWorkflow}
              >
                ▶ Run Workflow
              </Button>

              <div className="mt-6 text-center">
                <span className="text-[14px] text-[#444]">Try: </span>
                {[
                  'Update ticket JIR-123',
                  'Create branch for feature-x',
                  'Notify #releases'
                ].map((suggestion, i) => (
                  <button
                    key={i}
                    onClick={() => setCommand(suggestion)}
                    className="inline-block mx-1 my-1 px-3 py-1.5 glass-card rounded-full text-[13px] text-[#555] hover:text-[#888] hover:border-[rgba(255,255,255,0.12)] transition-colors"
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
            </motion.div>
          )}

          {phase === 'executing' && (
            <motion.div
              key="executing"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="max-w-[720px] mx-auto"
            >
              {/* Command Strip */}
              <div className="glass-card h-14 px-4 flex items-center gap-3 mb-6">
                <svg className="w-4 h-4 text-[#F59E0B]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                </svg>
                <span className="flex-1 text-[14px] font-mono text-[#888] truncate">{command}</span>
              </div>

              {/* Execution Timeline */}
              <div className="space-y-3">
                {steps.map((step, index) => (
                  <motion.div
                    key={step.id}
                    variants={fadeUp}
                    initial="hidden"
                    animate="show"
                    transition={{ delay: index * 0.08 }}
                    className="glass-card p-4 relative"
                    style={{
                      borderLeft: `3px solid ${SERVICE_COLORS[step.service].border}`
                    }}
                  >
                    <div className="flex items-start gap-4">
                      {/* Badge */}
                      <div
                        className="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-[13px] font-semibold transition-all"
                        style={{
                          border: step.status === 'pending' ? '2px solid #222' : `2px solid ${SERVICE_COLORS[step.service].border}`,
                          background: step.status === 'complete' ? SERVICE_COLORS[step.service].border : 'transparent',
                          color: step.status === 'complete' ? '#080808' : step.status === 'active' ? SERVICE_COLORS[step.service].border : '#444',
                          boxShadow: step.status === 'active' ? `0 0 12px ${SERVICE_COLORS[step.service].bg}` : 'none'
                        }}
                      >
                        {step.status === 'complete' ? <Check size={14} /> : index + 1}
                      </div>

                      {/* Content */}
                      <div className="flex-1 min-w-0">
                        <h4 className="text-[15px] font-semibold text-[#F0F0F0]">{step.name}</h4>
                        <p className={`text-[13px] ${step.status === 'active' ? 'text-[#888]' : 'text-[#555]'}`}>
                          {step.description}
                        </p>
                        {step.status === 'complete' && (
                          <motion.p
                            initial={{ opacity: 0, y: -4 }}
                            animate={{ opacity: 1, y: 0 }}
                            className="mt-2 text-[13px] font-mono"
                            style={{ color: SERVICE_COLORS[step.service].border }}
                          >
                            {step.completionDetail}
                          </motion.p>
                        )}
                      </div>

                      {/* Status Indicator */}
                      <div className="flex-shrink-0">
                        {step.status === 'active' && (
                          <Loader2 className="animate-spin" size={24} style={{ color: SERVICE_COLORS[step.service].border }} />
                        )}
                        {step.status === 'complete' && (
                          <motion.div initial={{ scale: 0 }} animate={{ scale: [0, 1.2, 1] }} transition={{ duration: 0.3 }}>
                            <Check size={24} style={{ color: SERVICE_COLORS[step.service].border }} />
                          </motion.div>
                        )}
                      </div>
                    </div>
                  </motion.div>
                ))}
              </div>
            </motion.div>
          )}

          {phase === 'complete' && (
            <motion.div
              key="complete"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="max-w-[720px] mx-auto"
            >
              {/* Timeline (faded) */}
              <div className="opacity-60 space-y-3 mb-8">
                {steps.map((step, index) => (
                  <div
                    key={step.id}
                    className="glass-card p-4"
                    style={{ borderLeft: `3px solid ${SERVICE_COLORS[step.service].border}` }}
                  >
                    <div className="flex items-center gap-4">
                      <div
                        className="w-8 h-8 rounded-full flex items-center justify-center"
                        style={{ background: SERVICE_COLORS[step.service].border }}
                      >
                        <Check size={14} className="text-[#080808]" />
                      </div>
                      <div className="flex-1">
                        <h4 className="text-[15px] font-semibold text-[#F0F0F0]">{step.name}</h4>
                        <p className="text-[13px] font-mono" style={{ color: SERVICE_COLORS[step.service].border }}>
                          {step.completionDetail}
                        </p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              {/* Completion Card */}
              <motion.div
                variants={fadeUpLarge}
                initial="hidden"
                animate="show"
                transition={{ delay: 0.4 }}
                className="glass-card--green p-8"
              >
                <div className="flex items-center gap-4 mb-6">
                  <div className="w-6 h-6 rounded-full bg-[#22C55E] flex items-center justify-center">
                    <Check size={14} className="text-[#080808]" />
                  </div>
                  <h2 className="text-[22px] font-semibold text-[#F0F0F0]">Workflow Completed</h2>
                </div>

                <div className="h-px bg-[rgba(255,255,255,0.06)] mb-6" />

                <div className="text-center mb-6">
                  <div className="flex items-baseline justify-center gap-2">
                    <span className="text-[64px] font-extrabold text-[#F0F0F0]">94</span>
                    <span className="text-[24px] text-[#444]">/100</span>
                  </div>
                  <p className="text-[12px] text-[#555] tracking-[0.08em] mt-2">DECISION QUALITY SCORE</p>
                  <div className="mt-5 max-w-md mx-auto">
                    <ScoreBar value={94} label="" showValue={false} />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
                  <ScoreBar value={87} label="Task Completion" />
                  <ScoreBar value={91} label="Decision Accuracy" />
                  <ScoreBar value={89} label="Execution Efficiency" />
                  <ScoreBar value={96} label="Context Relevance" />
                </div>

                <div className="text-center mb-6">
                  <div className="inline-flex items-center px-4 py-2 glass-card rounded-full">
                    <span className="text-[13px] text-[#22C55E] font-medium">Overall: Excellent</span>
                  </div>
                </div>

                <p className="text-[14px] text-[#666] text-center leading-[1.7] max-w-[560px] mx-auto mb-6">
                  All 5 steps executed successfully. The agent correctly identified the target branch, generated contextually accurate PR description, and routed notifications to the correct Slack channel.
                </p>

                <div className="flex flex-col sm:flex-row items-center justify-center gap-3">
                  <Button variant="ghost" onClick={resetWorkflow}>↺ Run Another Workflow</Button>
                </div>
              </motion.div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
