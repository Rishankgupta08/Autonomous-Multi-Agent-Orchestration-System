import { GitBranch, Ticket, Hash, User } from 'lucide-react';
import { useDefaultsStore } from '../store/defaultsStore';

export default function DefaultsChips() {
  const {
    githubOwner,
    githubDefaultRepo,
    jiraProjectKey,
    slackDefaultChannel,
    isLoaded
  } = useDefaultsStore();

  const activeChips = [
    { key: 'repo',    label: 'repo',    value: githubDefaultRepo,   icon: GitBranch },
    { key: 'jira',    label: 'jira',    value: jiraProjectKey,      icon: Ticket },
    { key: 'slack',   label: 'slack',   value: slackDefaultChannel, icon: Hash },
    { key: 'owner',   label: 'owner',   value: githubOwner,         icon: User },
  ].filter(chip => chip.value && chip.value.trim() !== '');

  if (!isLoaded) return null;

  if (activeChips.length > 0) {
    return (
      <div className="flex flex-wrap gap-2 mt-2">
        {activeChips.map(chip => {
          const ChipIcon = chip.icon;
          return (
            <span 
              key={chip.key}
              className="flex items-center gap-1.5 rounded-full bg-zinc-800 border border-zinc-700 text-xs text-zinc-400 px-3 py-1 cursor-default select-none"
              title="Default value — mention a different value in your prompt to override"
            >
              {chip.key === 'jira' ? (
                <span className="text-[10px] font-bold text-zinc-500 mr-0.5">J</span>
              ) : (
                <ChipIcon size={10} className="text-zinc-500" />
              )}
              <span className="text-zinc-500">{chip.label}:</span>
              <span className="text-zinc-300">{chip.value}</span>
            </span>
          );
        })}
        <span className="text-xs text-zinc-600 self-center ml-1">
          · mention different values in prompt to override
        </span>
      </div>
    );
  }

  return (
    <div className="mt-2">
      <span className="text-xs text-zinc-600">
        💡{' '}
        <a href="/integrations" className="text-zinc-400 underline underline-offset-2 hover:text-zinc-300 transition-colors">
          Set workspace defaults
        </a>
        {' '}to skip typing repo/channel every time
      </span>
    </div>
  );
}
