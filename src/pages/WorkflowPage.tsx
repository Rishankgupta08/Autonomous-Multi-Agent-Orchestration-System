import { useEffect } from 'react';
import { motion, AnimatePresence } from 'motion/react';
import { useAuthStore } from '../store/authStore';
import { useWorkflowStore } from '../store/workflowStore';
import { useDefaultsStore } from '../store/defaultsStore';
import Button from '../components/common/Button';
import ScoreBar from '../components/common/ScoreBar';
import DefaultsChips from '../components/DefaultsChips';
import CodeReviewPanel from '../components/CodeReviewPanel';
import { fadeUp, fadeUpLarge } from '../lib/animations';
import { Check, Loader2, X, GitPullRequest, Clock } from 'lucide-react';

const SERVICE_COLORS = {
  github: { border: '#22C55E', bg: 'rgba(34,197,94,0.08)' },
  jira:   { border: '#2D8EFF', bg: 'rgba(45,142,255,0.08)' },
  slack:  { border: '#7C3AED', bg: 'rgba(124,58,237,0.08)' },
  llm:    { border: '#F59E0B', bg: 'rgba(245,158,11,0.08)' },
};

type ServiceKey = keyof typeof SERVICE_COLORS;

function getServiceColor(tool: string) {
  return SERVICE_COLORS[tool as ServiceKey] || SERVICE_COLORS.github;
}

const SUGGESTIONS = [
  'Create a Jira ticket for the login bug and notify #dev-team',
  'Create a PR for the auth feature in project BACKEND',
  'Send a message to #releases about the new deployment',
];

export default function WorkflowPage() {
  const { user } = useAuthStore();
  const {
    phase,
    goal,
    workflowId,
    codeReviewActive,
    liveSteps,
    logs,
    score,
    overallStatus,
    error,
    setGoal,
    executeWorkflow,
    reset,
  } = useWorkflowStore();

  const { githubDefaultRepo, slackDefaultChannel, isLoaded, fetchDefaults } = useDefaultsStore();

  useEffect(() => {
    if (!isLoaded) fetchDefaults();
  }, [isLoaded, fetchDefaults]);

  const placeholderText = isLoaded && githubDefaultRepo
    ? `e.g. "Work on EC-23 and notify the team" — uses ${githubDefaultRepo}${slackDefaultChannel ? ', ' + slackDefaultChannel : ''} by default`
    : `e.g. "Create a PR in repo my-app for EC-22, update Jira, and notify #dev-team on Slack"`;

  const handleRun = async () => {
    if (!goal.trim()) return;
    await executeWorkflow(goal);
  };

  const getRatingLabel = (score: number) => {
    if (score >= 90) return 'Excellent';
    if (score >= 75) return 'Good';
    if (score >= 60) return 'Partial';
    return 'Poor';
  };

  return (
    <div className="min-h-screen bg-[#080808]">
      {/* Top Bar */}
      <div
        className="sticky top-0 z-40 h-14 border-b border-[rgba(255,255,255,0.05)]"
        style={{
          background: 'rgba(8,8,8,0.85)',
          backdropFilter: 'blur(16px)',
          WebkitBackdropFilter: 'blur(16px)',
        }}
      >
        <div className="h-full px-6 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-white font-bold text-[15px]">MOAE</span>
            <span className="text-[#333]">/</span>
            <span className="text-[14px] text-[#555]">Command Center</span>
          </div>
          {user && (
            <button className="relative w-8 h-8 rounded-full overflow-hidden border-2 border-[rgba(255,255,255,0.1)]">
              <img src={user.avatarUrl} alt={user.name} className="w-full h-full object-cover" />
              <span className="absolute bottom-0 right-0 w-2 h-2 bg-[#22C55E] rounded-full border-2 border-[#080808]" />
            </button>
          )}
        </div>
      </div>

      <div className="px-6 py-12">
        <AnimatePresence mode="wait">

          {/* ── INPUT PHASE ───────────────────────────────────────── */}
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
                  value={goal}
                  onChange={(e) => setGoal(e.target.value)}
                  placeholder={placeholderText}
                  className="w-full min-h-[160px] bg-transparent border-0 text-[16px] text-[#F0F0F0] placeholder:text-[#444] resize-none focus:outline-none leading-[1.7]"
                  style={{ fontFamily: 'var(--font-sans)' }}
                  maxLength={590}
                />
                <div className="border-t border-[rgba(255,255,255,0.06)] mt-4 pt-3 flex items-center justify-between">
                  <div className="h-7 px-2 glass-card rounded-lg flex items-center gap-1.5 text-[12px] text-[#555]">
                    <svg className="w-3 h-3" style={{ color: '#22C55E' }} fill="currentColor" viewBox="0 0 24 24">
                      <path fillRule="evenodd" d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z" clipRule="evenodd" />
                    </svg>
                    main
                  </div>
                  <span className="text-[12px] font-mono text-[#444]">{goal.length} / 590</span>
                </div>
              </div>

              <DefaultsChips />

              <Button
                variant="primary"
                className="!h-[52px] mx-auto"
                disabled={!goal.trim()}
                onClick={handleRun}
              >
                ▶ Run Workflow
              </Button>

              <div className="mt-6 text-center">
                <span className="text-[14px] text-[#444]">Try: </span>
                {SUGGESTIONS.map((s, i) => (
                  <button
                    key={i}
                    onClick={() => setGoal(s)}
                    className="inline-block mx-1 my-1 px-3 py-1.5 glass-card rounded-full text-[13px] text-[#555] hover:text-[#888] transition-colors"
                  >
                    {s.length > 40 ? s.substring(0, 40) + '...' : s}
                  </button>
                ))}
              </div>
            </motion.div>
          )}

          {/* ── EXECUTING PHASE ───────────────────────────────────── */}
          {phase === 'executing' && (
            <motion.div
              key="executing"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className={codeReviewActive ? 'w-full' : 'max-w-[720px] mx-auto w-full'}
            >
              <AnimatePresence mode="wait">
                {codeReviewActive && workflowId ? (
                  <motion.div
                    key="code-review"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.25 }}
                  >
                    <CodeReviewPanel workflowId={workflowId} />
                  </motion.div>
                ) : (
                  <motion.div
                    key="step-progress"
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    exit={{ opacity: 0 }}
                    transition={{ duration: 0.25 }}
                  >
                    {/* Command strip */}
                    <div className="glass-card h-14 px-4 flex items-center gap-3 mb-6">
                      <svg className="w-4 h-4 text-[#F59E0B]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
                      </svg>
                      <span className="flex-1 text-[14px] font-mono text-[#888] truncate">{goal}</span>
                      <Loader2 className="animate-spin text-[#F59E0B]" size={16} />
                    </div>

                    {/* Live log feed */}
                    {logs.length > 0 && (
                      <div className="glass-card p-3 mb-4 max-h-[120px] overflow-y-auto">
                        {logs.map((log, i) => (
                          <p key={i} className={`text-[12px] font-mono ${
                            log.cls === 'error' ? 'text-red-400' :
                            log.cls === 'warn'  ? 'text-yellow-400' : 'text-[#555]'
                          }`}>
                            → {log.msg}
                          </p>
                        ))}
                      </div>
                    )}

                    {/* Steps — real AI-generated from plan_ready */}
                    {liveSteps.length === 0 ? (
                      <div className="glass-card p-6 flex items-center justify-center gap-3">
                        <Loader2 className="animate-spin text-[#22C55E]" size={20} />
                        <span className="text-[14px] text-[#555]">AI is planning your workflow...</span>
                      </div>
                    ) : (
                      <div className="space-y-3">
                        {liveSteps.map((step, index) => {
                          const colors = getServiceColor(step.tool);
                          return (
                            <motion.div
                              key={index}
                              variants={fadeUp}
                              initial="hidden"
                              animate="show"
                              transition={{ delay: index * 0.08 }}
                              className="glass-card p-4"
                              style={{ borderLeft: `3px solid ${colors.border}` }}
                            >
                              <div className="flex items-start gap-4">
                                <div
                                  className="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-[13px] font-semibold transition-all"
                                  style={{
                                    border: step.status === 'pending'
                                      ? '2px solid #222'
                                      : `2px solid ${colors.border}`,
                                    background: step.status === 'success'
                                      ? colors.border
                                      : step.status === 'failed'
                                      ? '#EF4444'
                                      : 'transparent',
                                    color: step.status === 'success' || step.status === 'failed'
                                      ? '#080808'
                                      : step.status === 'active'
                                      ? colors.border : '#444',
                                  }}
                                >
                                  {step.status === 'success' && <Check size={14} />}
                                  {step.status === 'failed'  && <X size={14} />}
                                  {(step.status === 'pending' || step.status === 'active') && index + 1}
                                </div>

                                <div className="flex-1 min-w-0">
                                  <h4 className="text-[15px] font-semibold text-[#F0F0F0] capitalize">
                                    {step.tool} — {step.action}
                                  </h4>
                                  {step.status === 'failed' && step.error && (
                                    <p className="text-[12px] text-red-400 mt-1">{step.error}</p>
                                  )}
                                </div>

                                <div className="flex-shrink-0">
                                  {step.status === 'active' && (
                                    <Loader2 className="animate-spin" size={20} style={{ color: colors.border }} />
                                  )}
                                  {step.status === 'success' && (
                                    <Check size={20} style={{ color: colors.border }} />
                                  )}
                                  {step.status === 'failed' && (
                                    <X size={20} className="text-red-400" />
                                  )}
                                </div>
                              </div>
                            </motion.div>
                          );
                        })}
                      </div>
                    )}
                  </motion.div>
                )}
              </AnimatePresence>
            </motion.div>
          )}


          {/* ── AWAITING MERGE PHASE (from Step 10 webhook) ────────── */}
          {phase === 'awaiting_merge' && (
            <motion.div
              key="awaiting_merge"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="max-w-[720px] mx-auto text-center py-20"
            >
              <div className="glass-card p-10 flex flex-col items-center gap-6">
                <div className="w-14 h-14 rounded-full border-2 border-[#F59E0B] flex items-center justify-center">
                  <GitPullRequest size={28} className="text-[#F59E0B]" />
                </div>
                <h2 className="text-[22px] font-semibold text-[#F0F0F0]">
                  Waiting for PR Merge
                </h2>
                <p className="text-[14px] text-[#666] max-w-[420px] leading-[1.7]">
                  The workflow has created a Pull Request and is waiting for it to be
                  merged. Once merged, the system will automatically complete the
                  remaining steps via GitHub webhook.
                </p>
                <div className="flex items-center gap-2 text-[13px] text-[#555]">
                  <Clock size={14} />
                  <span>Listening for webhook event...</span>
                </div>
                <Button variant="ghost" onClick={reset}>← Start New Workflow</Button>
              </div>
            </motion.div>
          )}

          {/* ── COMPLETE PHASE ────────────────────────────────────── */}
          {phase === 'complete' && (
            <motion.div
              key="complete"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="max-w-[720px] mx-auto"
            >
              {/* Faded step timeline */}
              <div className="opacity-60 space-y-3 mb-8">
                {liveSteps.map((step, index) => {
                  const colors = getServiceColor(step.tool);
                  return (
                    <div
                      key={index}
                      className="glass-card p-4"
                      style={{ borderLeft: `3px solid ${colors.border}` }}
                    >
                      <div className="flex items-center gap-4">
                        <div
                          className="w-8 h-8 rounded-full flex items-center justify-center"
                          style={{
                            background: step.status === 'failed' ? '#EF4444' : colors.border,
                          }}
                        >
                          {step.status === 'failed'
                            ? <X size={14} className="text-white" />
                            : <Check size={14} className="text-[#080808]" />
                          }
                        </div>
                        <div className="flex-1">
                          <h4 className="text-[15px] font-semibold text-[#F0F0F0] capitalize">
                            {step.tool} — {step.action}
                          </h4>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Completion Card */}
              <motion.div
                variants={fadeUpLarge}
                initial="hidden"
                animate="show"
                transition={{ delay: 0.4 }}
                className={overallStatus === 'SUCCESS' ? 'glass-card--green p-8' : 'glass-card p-8 border border-red-900/30'}
              >
                <div className="flex items-center gap-4 mb-6">
                  <div className={`w-6 h-6 rounded-full flex items-center justify-center ${
                    overallStatus === 'SUCCESS' ? 'bg-[#22C55E]' : 'bg-red-500'
                  }`}>
                    {overallStatus === 'SUCCESS'
                      ? <Check size={14} className="text-[#080808]" />
                      : <X size={14} className="text-white" />
                    }
                  </div>
                  <h2 className="text-[22px] font-semibold text-[#F0F0F0]">
                    {overallStatus === 'SUCCESS' ? 'Workflow Completed' : 'Workflow Failed'}
                  </h2>
                </div>

                <div className="h-px bg-[rgba(255,255,255,0.06)] mb-6" />

                {score && (
                  <>
                    <div className="text-center mb-6">
                      <div className="flex items-baseline justify-center gap-2">
                        <span className="text-[64px] font-extrabold text-[#F0F0F0]">
                          {score.overall}
                        </span>
                        <span className="text-[24px] text-[#444]">/100</span>
                      </div>
                      <p className="text-[12px] text-[#555] tracking-[0.08em] mt-2">
                        DECISION QUALITY SCORE
                      </p>
                      <div className="mt-5 max-w-md mx-auto">
                        <ScoreBar value={score.overall} label="" showValue={false} />
                      </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
                      <ScoreBar value={score.taskCompletion}      label="Task Completion" />
                      <ScoreBar value={score.decisionAccuracy}    label="Decision Accuracy" />
                      <ScoreBar value={score.executionEfficiency} label="Execution Efficiency" />
                      <ScoreBar value={score.contextRelevance}    label="Context Relevance" />
                    </div>

                    <div className="text-center mb-6">
                      <div className="inline-flex items-center px-4 py-2 glass-card rounded-full">
                        <span className={`text-[13px] font-medium ${
                          overallStatus === 'SUCCESS' ? 'text-[#22C55E]' : 'text-red-400'
                        }`}>
                          Overall: {getRatingLabel(score.overall)}
                        </span>
                      </div>
                    </div>

                    {score.summary && (
                      <p className="text-[14px] text-[#666] text-center leading-[1.7] max-w-[560px] mx-auto mb-6">
                        {score.summary}
                      </p>
                    )}
                  </>
                )}

                <div className="flex justify-center">
                  <Button variant="ghost" onClick={reset}>↺ Run Another Workflow</Button>
                </div>
              </motion.div>
            </motion.div>
          )}

          {/* ── ERROR PHASE ───────────────────────────────────────── */}
          {phase === 'error' && (
            <motion.div
              key="error"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="max-w-[720px] mx-auto text-center py-20"
            >
              <div className="glass-card p-10">
                <X size={40} className="text-red-400 mx-auto mb-4" />
                <h2 className="text-[20px] font-semibold text-[#F0F0F0] mb-2">
                  Something went wrong
                </h2>
                <p className="text-[14px] text-[#666] mb-6">
                  {error || 'An unexpected error occurred'}
                </p>
                <Button variant="ghost" onClick={reset}>← Try Again</Button>
              </div>
            </motion.div>
          )}

        </AnimatePresence>
      </div>
    </div>
  );
}
