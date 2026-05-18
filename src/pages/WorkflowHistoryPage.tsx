// src/pages/WorkflowHistoryPage.tsx
import { useEffect } from 'react';
import { motion } from 'motion/react';
import { useWorkflowStore } from '../store/workflowStore';
import { Check, X, Clock, GitPullRequest, Loader2 } from 'lucide-react';

const STATUS_CONFIG: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  SUCCESS:        { label: 'Success',        color: '#22C55E', icon: <Check size={12} /> },
  COMPLETED:      { label: 'Completed',      color: '#22C55E', icon: <Check size={12} /> },
  FAILED:         { label: 'Failed',         color: '#EF4444', icon: <X size={12} /> },
  PARTIAL:        { label: 'Partial',        color: '#F59E0B', icon: <X size={12} /> },
  RUNNING:        { label: 'Running',        color: '#2D8EFF', icon: <Loader2 size={12} className="animate-spin" /> },
  AWAITING_MERGE: { label: 'Awaiting Merge', color: '#F59E0B', icon: <GitPullRequest size={12} /> },
};

const TOOL_COLORS: Record<string, string> = {
  github: '#22C55E',
  jira:   '#2D8EFF',
  slack:  '#7C3AED',
  llm:    '#F59E0B',
};

function formatDate(dateStr: string) {
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffHrs = Math.floor(diffMs / (1000 * 60 * 60));
  if (diffHrs < 1)  return 'Just now';
  if (diffHrs < 24) return `${diffHrs}h ago`;
  const diffDays = Math.floor(diffHrs / 24);
  if (diffDays < 7) return `${diffDays}d ago`;
  return date.toLocaleDateString();
}

export default function WorkflowHistoryPage() {
  const { history, historyLoading, fetchHistory } = useWorkflowStore();

  useEffect(() => {
    fetchHistory();
  }, []);

  return (
    <div className="min-h-screen bg-[#080808]">
      {/* Top Bar */}
      <div
        className="sticky top-0 z-40 h-14 border-b border-[rgba(255,255,255,0.05)]"
        style={{ background: 'rgba(8,8,8,0.85)', backdropFilter: 'blur(16px)' }}
      >
        <div className="h-full px-6 flex items-center">
          <span className="text-white font-bold text-[15px]">MOAE</span>
          <span className="text-[#333] mx-2">/</span>
          <span className="text-[14px] text-[#555]">History</span>
        </div>
      </div>

      <div className="max-w-[900px] mx-auto px-6 py-10">
        <h1 className="text-[24px] font-semibold text-[#F0F0F0] mb-6">
          Workflow History
        </h1>

        {historyLoading && (
          <div className="flex items-center justify-center py-20">
            <Loader2 className="animate-spin text-[#22C55E]" size={28} />
          </div>
        )}

        {!historyLoading && history.length === 0 && (
          <div className="glass-card p-10 text-center">
            <p className="text-[14px] text-[#555]">
              No workflows yet. Run your first workflow from the Command Center.
            </p>
          </div>
        )}

        {!historyLoading && history.length > 0 && (
          <div className="space-y-3">
            {history.map((run, i) => {
              const statusCfg = STATUS_CONFIG[run.status] || {
                label: run.status, color: '#555', icon: <Clock size={12} />
              };
              return (
                <motion.div
                  key={run.id}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.04 }}
                  className="glass-card p-5"
                >
                  <div className="flex items-start gap-4">
                    {/* Status badge */}
                    <div
                      className="flex-shrink-0 flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-medium mt-0.5"
                      style={{
                        background: `${statusCfg.color}18`,
                        color: statusCfg.color,
                        border: `1px solid ${statusCfg.color}44`,
                      }}
                    >
                      {statusCfg.icon}
                      {statusCfg.label}
                    </div>

                    {/* Goal */}
                    <div className="flex-1 min-w-0">
                      <p className="text-[14px] text-[#D0D0D0] leading-snug line-clamp-2">
                        {run.goal}
                      </p>

                      {/* Step tool dots */}
                      {run.steps.length > 0 && (
                        <div className="flex items-center gap-1.5 mt-2">
                          {run.steps.map((step, j) => (
                            <div
                              key={j}
                              className="w-2 h-2 rounded-full"
                              style={{
                                background: step.status === 'SUCCESS'
                                  ? TOOL_COLORS[step.tool] || '#555'
                                  : step.status === 'FAILED'
                                  ? '#EF4444' : '#333',
                              }}
                              title={`${step.tool}: ${step.action} — ${step.status}`}
                            />
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Score + time */}
                    <div className="flex-shrink-0 text-right">
                      {run.score != null && (
                        <p className="text-[18px] font-bold text-[#F0F0F0]">
                          {run.score}
                          <span className="text-[12px] text-[#444] font-normal">/100</span>
                        </p>
                      )}
                      <p className="text-[12px] text-[#444] mt-1">
                        {formatDate(run.createdAt)}
                      </p>
                    </div>
                  </div>
                </motion.div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
