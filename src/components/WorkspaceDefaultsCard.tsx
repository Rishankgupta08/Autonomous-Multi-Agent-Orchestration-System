import React, { useEffect, useState } from 'react';
import { useDefaultsStore } from '../store/defaultsStore';
import Input from './common/Input';
import Button from './common/Button';
import { Loader2 } from 'lucide-react';

function getRelativeTime(date: Date | null) {
  if (!date) return '';
  const now = new Date();
  const diffInSeconds = Math.floor((now.getTime() - date.getTime()) / 1000);

  if (diffInSeconds < 60) return 'just now';
  if (diffInSeconds < 3600) {
    const minutes = Math.floor(diffInSeconds / 60);
    return `${minutes} minute${minutes > 1 ? 's' : ''} ago`;
  }
  if (diffInSeconds < 86400) {
    const hours = Math.floor(diffInSeconds / 3600);
    return `${hours} hour${hours > 1 ? 's' : ''} ago`;
  }
  const days = Math.floor(diffInSeconds / 86400);
  return `${days} day${days > 1 ? 's' : ''} ago`;
}

export default function WorkspaceDefaultsCard() {
  const {
    githubOwner,
    githubDefaultRepo,
    jiraProjectKey,
    slackDefaultChannel,
    isLoaded,
    isSaving,
    lastSavedAt,
    fetchDefaults,
    saveDefaults
  } = useDefaultsStore();

  const [formData, setFormData] = useState({
    githubOwner: '',
    githubDefaultRepo: '',
    jiraProjectKey: '',
    slackDefaultChannel: ''
  });

  const [showToast, setShowToast] = useState(false);
  const [timeText, setTimeText] = useState('');

  useEffect(() => {
    if (!isLoaded) {
      fetchDefaults();
    }
  }, [isLoaded, fetchDefaults]);

  useEffect(() => {
    if (isLoaded) {
      setFormData({
        githubOwner,
        githubDefaultRepo,
        jiraProjectKey,
        slackDefaultChannel
      });
    }
  }, [isLoaded, githubOwner, githubDefaultRepo, jiraProjectKey, slackDefaultChannel]);

  useEffect(() => {
    const interval = setInterval(() => {
      setTimeText(getRelativeTime(lastSavedAt));
    }, 10000); // Update every 10 seconds
    setTimeText(getRelativeTime(lastSavedAt));
    
    return () => clearInterval(interval);
  }, [lastSavedAt]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    await saveDefaults(formData);
    setShowToast(true);
    setTimeout(() => setShowToast(false), 2000);
  };

  const hasSavedGithubOwner = !!githubOwner;
  const hasSavedGithubRepo = !!githubDefaultRepo;
  const hasSavedJira = !!jiraProjectKey;
  const hasSavedSlack = !!slackDefaultChannel;

  return (
    <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-6 relative">
      {showToast && (
        <div className="absolute top-4 right-4 bg-emerald-500/20 border border-emerald-500/50 text-emerald-400 text-sm px-3 py-1.5 rounded-full transition-opacity duration-300">
          Defaults saved ✓
        </div>
      )}

      <div className="mb-6">
        <h3 className="text-[17px] font-semibold text-[#F0F0F0] flex items-center gap-2">
          <span>⚙️</span> Workspace Defaults
        </h3>
        <p className="text-[13px] text-[#555] mt-1">
          Set once. MOAE uses these automatically in every workflow.
          <br />
          Override any value by mentioning it in your prompt.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <label className="text-sm text-zinc-400">GitHub Owner</label>
              {hasSavedGithubOwner && <div className="w-2 h-2 rounded-full bg-emerald-500" />}
            </div>
            <Input
              value={formData.githubOwner}
              onChange={(e) => setFormData({ ...formData, githubOwner: e.target.value })}
              placeholder="e.g. facebook"
            />
          </div>

          <div>
            <div className="flex items-center gap-2 mb-1">
              <label className="text-sm text-zinc-400">Default Repository</label>
              {hasSavedGithubRepo && <div className="w-2 h-2 rounded-full bg-emerald-500" />}
            </div>
            <Input
              value={formData.githubDefaultRepo}
              onChange={(e) => setFormData({ ...formData, githubDefaultRepo: e.target.value })}
              placeholder="e.g. react"
            />
          </div>

          <div>
            <div className="flex items-center gap-2 mb-1" title="e.g. EC, PROJ, DEV">
              <label className="text-sm text-zinc-400">Jira Project Key</label>
              {hasSavedJira && <div className="w-2 h-2 rounded-full bg-emerald-500" />}
            </div>
            <Input
              value={formData.jiraProjectKey}
              onChange={(e) => setFormData({ ...formData, jiraProjectKey: e.target.value })}
              placeholder="e.g. PROJ"
            />
          </div>

          <div>
            <div className="flex items-center gap-2 mb-1">
              <label className="text-sm text-zinc-400">Slack Channel</label>
              {hasSavedSlack && <div className="w-2 h-2 rounded-full bg-emerald-500" />}
            </div>
            <Input
              value={formData.slackDefaultChannel}
              onChange={(e) => setFormData({ ...formData, slackDefaultChannel: e.target.value })}
              placeholder="e.g. #dev-team"
            />
          </div>
        </div>

        <div className="bg-zinc-800/50 rounded-lg p-3 mt-4 border border-zinc-700/50">
          <p className="text-sm text-zinc-400">
            <span className="text-zinc-300">💡 Tip:</span> Say "work on EC-23" instead of "work on EC-23 in repo testing for Rishankgupta08"
          </p>
        </div>

        <div className="flex items-center justify-end gap-4 pt-2">
          {timeText && (
            <span className="text-xs text-zinc-500">
              Last saved: {timeText}
            </span>
          )}
          <Button type="submit" disabled={isSaving} className="!w-auto !px-6">
            {isSaving ? <Loader2 className="animate-spin mx-auto" size={14} /> : 'Save Defaults'}
          </Button>
        </div>
      </form>
    </div>
  );
}
