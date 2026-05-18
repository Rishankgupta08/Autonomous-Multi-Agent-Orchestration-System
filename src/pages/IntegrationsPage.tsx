import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import { useAuthStore } from '../store/authStore';
import { useIntegrationStore } from '../store/integrationStore';
import Input from '../components/common/Input';
import Button from '../components/common/Button';
import StatusChip from '../components/common/StatusChip';
import { fadeUp, staggerContainer } from '../lib/animations';
import { Loader2 } from 'lucide-react';

export default function IntegrationsPage() {
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const {
    jiraLoading, slackLoading,
    jiraError, slackError,
    connectJira, disconnectJira,
    connectSlack, disconnectSlack,
  } = useIntegrationStore();

  const jiraConnected = user?.jiraConnected ?? false;
  const slackConnected = user?.slackConnected ?? false;

  const [jiraData, setJiraData] = useState({ domain: '', email: '', token: '' });
  const [slackData, setSlackData] = useState({ botToken: '' });

  const handleJiraConnect = async (e: React.FormEvent) => {
    e.preventDefault();
    const success = await connectJira({ domain: jiraData.domain, email: jiraData.email, apiToken: jiraData.token });
    if (success) {
      setJiraData({ domain: '', email: '', token: '' });
    }
  };

  const handleSlackConnect = async (e: React.FormEvent) => {
    e.preventDefault();
    const success = await connectSlack({ botToken: slackData.botToken });
    if (success) {
      setSlackData({ botToken: '' });
    }
  };

  const allConnected = jiraConnected && slackConnected;

  return (
    <div className="min-h-screen bg-[#080808] px-6 py-16">
      <motion.div
        className="max-w-[720px] mx-auto"
        variants={staggerContainer(0.05)}
        initial="hidden"
        animate="show"
      >
        <motion.div variants={fadeUp}>
          <Link to="/" className="text-[13px] text-[#555] hover:text-[#888] transition-colors">
            ← Setup
          </Link>
        </motion.div>

        <motion.div variants={fadeUp} className="mt-4">
          <h1 className="text-[clamp(36px,5vw,48px)] font-bold text-[#F0F0F0] tracking-[-0.03em]">
            Connect your tools
          </h1>
          <p className="mt-3 text-[16px] text-[#666]">
            MOAE needs access to your services to automate workflows.
          </p>
          <div className="mt-6 inline-flex items-center px-3 py-1.5 glass-card rounded-full">
            <span className="text-[12px] font-mono text-[#555]">Step 2 of 3</span>
          </div>
        </motion.div>

        <motion.div variants={fadeUp} className="my-8">
          <div className="h-px bg-[rgba(255,255,255,0.06)]" />
        </motion.div>

        {/* GitHub Connected Banner */}
        <motion.div
          variants={fadeUp}
          className="inline-flex items-center gap-3 px-4 py-2.5 glass-card rounded-[10px] mb-6"
        >
          <svg className="w-4 h-4" style={{ color: '#22C55E' }} fill="currentColor" viewBox="0 0 24 24">
            <path fillRule="evenodd" d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z" clipRule="evenodd" />
          </svg>
          <span className="text-[13px] font-mono text-[#888]">github.com/{user?.githubLogin}</span>
          <StatusChip variant="connected-green" />
        </motion.div>

        {/* Jira Integration */}
        <motion.div variants={fadeUp} className={`glass-card p-6 mb-6 ${jiraConnected ? 'glass-card--blue' : ''}`}>
          <div className="flex items-start justify-between mb-5">
            <div className="flex items-center gap-3">
              <svg className="w-6 h-6" style={{ color: '#2D8EFF' }} viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0L1.608 10.392l2.4 2.4L12 4.8l7.992 7.992 2.4-2.4L12 0zm0 9.6L4.8 16.8l2.4 2.4 4.8-4.8 4.8 4.8 2.4-2.4L12 9.6z"/>
              </svg>
              <div>
                <h3 className="text-[17px] font-semibold text-[#F0F0F0]">Jira</h3>
                <p className="text-[13px] text-[#555]">Atlassian</p>
              </div>
            </div>
            <StatusChip variant={jiraConnected ? 'connected-blue' : 'disconnected'} />
          </div>

          <div className="h-px bg-[rgba(255,255,255,0.06)] mb-5" />

          {!jiraConnected ? (
            <form onSubmit={handleJiraConnect} className="space-y-3">
              <Input
                label="Jira Domain"
                placeholder="yourcompany.atlassian.net"
                value={jiraData.domain}
                onChange={(e) => setJiraData({ ...jiraData, domain: e.target.value })}
                required
              />
              <Input
                label="Account Email"
                type="email"
                placeholder="you@company.com"
                value={jiraData.email}
                onChange={(e) => setJiraData({ ...jiraData, email: e.target.value })}
                required
              />
              <div>
                <Input
                  label="API Token"
                  type="password"
                  monoFont
                  placeholder="••••••••••••••••"
                  value={jiraData.token}
                  onChange={(e) => setJiraData({ ...jiraData, token: e.target.value })}
                  required
                />
                <a
                  href="https://id.atlassian.com/manage-profile/security/api-tokens"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-block mt-1 text-[12px] text-[#2D8EFF] hover:underline"
                >
                  Generate API token →
                </a>
              </div>
              {jiraError && <p className="text-[13px] text-red-400 mt-2">{jiraError}</p>}
              <div className="pt-2">
                <Button
                  type="submit"
                  variant="blue"
                  fullWidth
                  disabled={jiraLoading}
                >
                  {jiraLoading ? <Loader2 className="animate-spin mx-auto" size={14} /> : 'Connect Jira'}
                </Button>
              </div>
            </form>
          ) : (
            <div className="flex items-center justify-between">
              <p className="text-[12px] text-[#555] font-mono">
                Connected
              </p>
              <Button type="button" variant="ghost" onClick={() => disconnectJira()} className="!h-8 !text-[12px] !text-red-400 hover:!bg-red-400/10">
                Disconnect
              </Button>
            </div>
          )}
        </motion.div>

        {/* Slack Integration */}
        <motion.div variants={fadeUp} className={`glass-card p-6 ${slackConnected ? 'glass-card--purple' : ''}`}>
          <div className="flex items-start justify-between mb-5">
            <div className="flex items-center gap-3">
              <svg className="w-6 h-6" style={{ color: '#7C3AED' }} viewBox="0 0 24 24" fill="currentColor">
                <path d="M5.042 15.165a2.528 2.528 0 0 1-2.52 2.523A2.528 2.528 0 0 1 0 15.165a2.527 2.527 0 0 1 2.522-2.52h2.52v2.52zM6.313 15.165a2.527 2.527 0 0 1 2.521-2.52 2.527 2.527 0 0 1 2.521 2.52v6.313A2.528 2.528 0 0 1 8.834 24a2.528 2.528 0 0 1-2.521-2.522v-6.313zM8.834 5.042a2.528 2.528 0 0 1-2.521-2.52A2.528 2.528 0 0 1 8.834 0a2.528 2.528 0 0 1 2.521 2.522v2.52H8.834zM8.834 6.313a2.528 2.528 0 0 1 2.521 2.521 2.528 2.528 0 0 1-2.521 2.521H2.522A2.528 2.528 0 0 1 0 8.834a2.528 2.528 0 0 1 2.522-2.521h6.312zM18.956 8.834a2.528 2.528 0 0 1 2.522-2.521A2.528 2.528 0 0 1 24 8.834a2.528 2.528 0 0 1-2.522 2.521h-2.522V8.834zM17.688 8.834a2.528 2.528 0 0 1-2.523 2.521 2.527 2.527 0 0 1-2.52-2.521V2.522A2.527 2.527 0 0 1 15.165 0a2.528 2.528 0 0 1 2.523 2.522v6.312zM15.165 18.956a2.528 2.528 0 0 1 2.523 2.522A2.528 2.528 0 0 1 15.165 24a2.527 2.527 0 0 1-2.52-2.522v-2.522h2.52zM15.165 17.688a2.527 2.527 0 0 1-2.52-2.523 2.526 2.526 0 0 1 2.52-2.52h6.313A2.527 2.527 0 0 1 24 15.165a2.528 2.528 0 0 1-2.522 2.523h-6.313z"/>
              </svg>
              <div>
                <h3 className="text-[17px] font-semibold text-[#F0F0F0]">Slack</h3>
                <p className="text-[13px] text-[#555]">Workspace Bot</p>
              </div>
            </div>
            <StatusChip variant={slackConnected ? 'connected-purple' : 'disconnected'} />
          </div>

          <div className="h-px bg-[rgba(255,255,255,0.06)] mb-5" />

          {!slackConnected ? (
            <form onSubmit={handleSlackConnect} className="space-y-3">
              <div>
                <Input
                  label="Bot Token"
                  type="password"
                  monoFont
                  placeholder="xoxb-••••-••••-••••-••••••••••••"
                  value={slackData.botToken}
                  onChange={(e) => setSlackData({ ...slackData, botToken: e.target.value })}
                  required
                />
                <div className="mt-1 space-y-1">
                  <a
                    href="https://api.slack.com/apps"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="block text-[12px] text-[#7C3AED] hover:underline"
                  >
                    Create a Slack App →
                  </a>
                </div>
              </div>
              {slackError && <p className="text-[13px] text-red-400 mt-2">{slackError}</p>}
              <div className="pt-2">
                <Button
                  type="submit"
                  variant="purple"
                  fullWidth
                  disabled={slackLoading}
                >
                  {slackLoading ? <Loader2 className="animate-spin mx-auto" size={14} /> : 'Connect Slack'}
                </Button>
              </div>
            </form>
          ) : (
            <div className="flex items-center justify-between">
              <p className="text-[12px] text-[#555] font-mono">
                Connected
              </p>
              <Button type="button" variant="ghost" onClick={() => disconnectSlack()} className="!h-8 !text-[12px] !text-red-400 hover:!bg-red-400/10">
                Disconnect
              </Button>
            </div>
          )}
        </motion.div>

        {/* Proceed Button */}
        {allConnected && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
            className="mt-8"
          >
            <Button
              variant="primary"
              fullWidth
              onClick={() => navigate('/workflow')}
              className="!h-14 !rounded-xl !text-[16px]"
            >
              → Launch Command Center
            </Button>
          </motion.div>
        )}
      </motion.div>
    </div>
  );
}
