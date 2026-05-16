import { Link } from 'react-router-dom';
import { motion } from 'motion/react';
import { useAuthStore } from '../store/authStore';
import { useIntegrationStore } from '../store/integrationStore';
import { fadeUp, staggerContainer } from '../lib/animations';
import Navbar from '../components/layout/Navbar';

export default function ProfilePage() {
  const { user } = useAuthStore();
  const { github, jira, slack } = useIntegrationStore();

  return (
    <div className="min-h-screen bg-[#080808]">
      <Navbar />

      <div className="max-w-[680px] mx-auto px-6 py-16">
        <motion.div
          variants={staggerContainer(0.05)}
          initial="hidden"
          animate="show"
        >
          <motion.div variants={fadeUp}>
            <Link to="/workflow" className="text-[13px] text-[#555] hover:text-[#888] transition-colors">
              ← Command Center
            </Link>
          </motion.div>

          {/* Profile Header */}
          <motion.div variants={fadeUp} className="glass-card p-8 text-center mt-6">
            <img
              src={user?.avatarUrl}
              alt={user?.name}
              className="w-[72px] h-[72px] rounded-full border-[3px] border-[rgba(255,255,255,0.1)] mx-auto mb-4"
            />
            <h1 className="text-[24px] font-semibold text-[#F0F0F0] mb-1">{user?.name}</h1>
            <p className="text-[16px] text-[#666] mb-1">{user?.company}</p>
            <p className="text-[13px] font-mono text-[#555]">{user?.email}</p>

            <div className="flex items-center justify-center gap-2 mt-5 flex-wrap">
              <div className="px-2.5 py-1 rounded-full text-[12px] font-mono border" style={{ borderColor: 'rgba(34,197,94,0.2)', background: 'rgba(34,197,94,0.04)', color: '#888' }}>
                ⬡ GitHub · @{github.username}
              </div>
              <div className="px-2.5 py-1 rounded-full text-[12px] font-mono border" style={{ borderColor: 'rgba(45,142,255,0.2)', background: 'rgba(45,142,255,0.04)', color: '#888' }}>
                ◆ Jira · {jira.accountId}
              </div>
              <div className="px-2.5 py-1 rounded-full text-[12px] font-mono border" style={{ borderColor: 'rgba(124,58,237,0.2)', background: 'rgba(124,58,237,0.04)', color: '#888' }}>
                # Slack · {slack.teamName}
              </div>
            </div>
          </motion.div>

          {/* Credentials */}
          <motion.div variants={fadeUp} className="glass-card p-6 mt-4">
            <h3 className="text-[11px] font-semibold text-[#444] tracking-[0.12em] mb-4">CONNECTED CREDENTIALS</h3>

            <div className="space-y-0">
              {[
                { label: 'GitHub Token', value: 'ghp_**********************abc1' },
                { label: 'Jira API Token', value: '••••••••••••••••••••••••xyz2' },
                { label: 'Slack Bot Token', value: 'xoxb-**********************def3' }
              ].map((item, i) => (
                <div
                  key={i}
                  className="flex items-center gap-4 py-3 border-b border-[rgba(255,255,255,0.04)] last:border-0"
                >
                  <span className="text-[14px] text-[#F0F0F0] w-[120px] flex-shrink-0">{item.label}</span>
                  <span className="flex-1 text-[13px] font-mono text-[#555] truncate">{item.value}</span>
                  <button className="text-[#444] hover:text-[#888] transition-colors">
                    <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                    </svg>
                  </button>
                </div>
              ))}
            </div>
          </motion.div>

          {/* Account Stats */}
          <motion.div variants={fadeUp} className="glass-card p-6 mt-4">
            <h3 className="text-[11px] font-semibold text-[#444] tracking-[0.12em] mb-4">ACCOUNT OVERVIEW</h3>

            <div className="grid grid-cols-3 divide-x divide-[rgba(255,255,255,0.06)]">
              {[
                { label: 'Total Workflows', value: '12' },
                { label: 'Avg Quality Score', value: '89/100' },
                { label: 'Success Rate', value: '91%' }
              ].map((stat, i) => (
                <div key={i} className="text-center px-4">
                  <div className="text-[28px] font-bold text-[#F0F0F0]">{stat.value}</div>
                  <div className="text-[12px] text-[#555] mt-1">{stat.label}</div>
                </div>
              ))}
            </div>
          </motion.div>

          {/* Danger Zone */}
          <motion.div variants={fadeUp} className="glass-card--error p-5 mt-6">
            <h3 className="text-[12px] font-semibold text-[#EF4444] tracking-wider mb-4">DANGER ZONE</h3>

            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-[14px] text-[#888]">Disconnect all integrations</span>
                <button className="px-4 py-2 text-[13px] border border-[rgba(239,68,68,0.25)] text-[#EF4444] rounded-lg hover:border-[rgba(239,68,68,0.5)] transition-colors">
                  Disconnect
                </button>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-[14px] text-[#888]">Delete account</span>
                <button className="px-4 py-2 text-[13px] border border-[rgba(239,68,68,0.25)] text-[#EF4444] rounded-lg hover:border-[rgba(239,68,68,0.5)] transition-colors">
                  Delete
                </button>
              </div>
            </div>
          </motion.div>
        </motion.div>
      </div>
    </div>
  );
}
