import { motion } from 'motion/react';
import { Link } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { MOCK_WORKFLOWS } from '../lib/mockData';
import Navbar from '../components/layout/Navbar';
import ScoreBar from '../components/common/ScoreBar';
import Button from '../components/common/Button';
import { fadeUp, staggerContainer } from '../lib/animations';
import { Check, X } from 'lucide-react';
import { formatDistanceToNow } from 'date-fns';

export default function WorkflowHistoryPage() {
  const { isAuthenticated } = useAuthStore();

  if (!isAuthenticated) {
    return null;
  }

  const SERVICE_COLORS = {
    github: '#22C55E',
    jira: '#2D8EFF',
    slack: '#7C3AED'
  };

  return (
    <div className="min-h-screen bg-[#080808]">
      <Navbar />

      <div className="max-w-[900px] mx-auto px-6 py-16">
        <motion.div
          variants={staggerContainer(0.05)}
          initial="hidden"
          animate="show"
        >
          <motion.div variants={fadeUp} className="flex items-center justify-between mb-6">
            <h1 className="text-[clamp(36px,5vw,48px)] font-bold text-[#F0F0F0]">Workflow History</h1>
            <div className="glass-card px-3 py-1.5 rounded-full">
              <span className="text-[13px] font-mono text-[#555]">{MOCK_WORKFLOWS.length} workflows</span>
            </div>
          </motion.div>

          <motion.div variants={fadeUp} className="flex items-center gap-3 mb-6">
            <button className="px-3.5 py-1.5 rounded-full text-[13px] bg-[rgba(255,255,255,0.08)] border border-[rgba(255,255,255,0.12)] text-[#F0F0F0]">
              All
            </button>
            <button className="px-3.5 py-1.5 rounded-full text-[13px] text-[#555] hover:text-[#888] transition-colors">
              Success
            </button>
            <button className="px-3.5 py-1.5 rounded-full text-[13px] text-[#555] hover:text-[#888] transition-colors">
              Failed
            </button>
          </motion.div>

          {/* Workflow Cards */}
          <div className="space-y-3">
            {MOCK_WORKFLOWS.map((workflow, index) => (
              <motion.div
                key={workflow.id}
                variants={fadeUp}
                transition={{ delay: index * 0.06 }}
                className={`glass-card p-5 hover:border-[rgba(255,255,255,0.1)] hover:-translate-y-0.5 transition-all cursor-pointer ${
                  workflow.status === 'failed' ? 'border-l-[3px] !border-l-[#EF4444]' : ''
                }`}
              >
                {/* Header */}
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2 text-[13px]">
                    <span className={workflow.status === 'success' ? 'text-[#22C55E]' : 'text-[#EF4444]'}>●</span>
                    <span className={workflow.status === 'success' ? 'text-[#22C55E]' : 'text-[#EF4444]'}>
                      {workflow.status === 'success' ? 'Success' : 'Failed'}
                    </span>
                    <span className="text-[#333]">·</span>
                    <span className="text-[#555]">{formatDistanceToNow(workflow.createdAt, { addSuffix: true })}</span>
                  </div>
                  <div className="glass-card px-3 py-1 rounded-full">
                    <span className="text-[12px] font-mono text-[#888]">{workflow.score.overall} / 100</span>
                  </div>
                </div>

                {/* Command */}
                <p className="text-[15px] text-[#888] mb-4 line-clamp-2">{workflow.command}</p>

                {/* Score Bars */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-4">
                  <ScoreBar value={workflow.score.taskCompletion} label="Task Completion" showValue />
                  <ScoreBar value={workflow.score.decisionAccuracy} label="Decision Accuracy" showValue />
                  <ScoreBar value={workflow.score.executionEfficiency} label="Efficiency" showValue />
                </div>

                {/* Footer */}
                <div className="flex items-center justify-between pt-4 border-t border-[rgba(255,255,255,0.06)]">
                  {/* Step Indicators */}
                  <div className="flex items-center gap-1.5">
                    {workflow.steps.map((step) => (
                      <div
                        key={step.id}
                        className="w-5 h-5 rounded-full flex items-center justify-center text-[10px]"
                        style={{
                          background: step.status === 'complete'
                            ? SERVICE_COLORS[step.service]
                            : step.status === 'error'
                            ? '#EF4444'
                            : 'transparent',
                          border: step.status === 'pending' ? '1px solid #333' : 'none'
                        }}
                      >
                        {step.status === 'complete' && <Check size={10} className="text-[#080808]" />}
                        {step.status === 'error' && <X size={10} className="text-white" />}
                      </div>
                    ))}
                  </div>

                  {/* Actions */}
                  <div className="flex items-center gap-2">
                    <Button variant="ghost" className="!h-8 !px-3 !text-[13px]">→ View Details</Button>
                  </div>
                </div>

                {/* Error Message */}
                {workflow.errorMessage && (
                  <div className="mt-3 flex items-center gap-2 text-[13px] text-[#EF4444]">
                    <span>⚠</span>
                    <span>Error at Step 3: {workflow.errorMessage}</span>
                  </div>
                )}
              </motion.div>
            ))}
          </div>

          {/* Empty State (hidden when workflows exist) */}
          {MOCK_WORKFLOWS.length === 0 && (
            <motion.div
              variants={fadeUp}
              className="text-center py-20"
            >
              <div className="w-12 h-12 mx-auto mb-4 text-[#1C1C1C]">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1">
                  <path d="M13 2L3 14h8l-1 8 10-12h-8l1-8z" />
                </svg>
              </div>
              <h3 className="text-[20px] font-semibold text-[#333] mb-2">No workflows yet</h3>
              <p className="text-[14px] text-[#444] mb-6">Run your first workflow to see results here.</p>
              <Link to="/workflow">
                <Button variant="ghost">→ Start a Workflow</Button>
              </Link>
            </motion.div>
          )}
        </motion.div>
      </div>
    </div>
  );
}
