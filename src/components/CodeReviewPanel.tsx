import React, { useState, useEffect, useRef } from 'react';
import Editor from '@monaco-editor/react';
import { motion } from 'framer-motion';
import { FileCode, Sparkles, Send, Loader2, RefreshCw, GitBranch } from 'lucide-react';
import { useWorkflowStore } from '../store/workflowStore';

export default function CodeReviewPanel({ workflowId }: { workflowId: string }) {
  const {
    pendingCode,
    pendingFilePath,
    pendingLanguage,
    isIterating,
    isApproving,
    iterationChat,
    iterateCode,
    approveCode,
    lastIterationAt,
  } = useWorkflowStore();

  const [localCode, setLocalCode] = useState(pendingCode ?? '');
  const [chatInput, setChatInput] = useState('');
  const chatEndRef = useRef<HTMLDivElement>(null);

  // Sync localCode when pendingCode changes (after AI iteration)
  useEffect(() => {
    if (pendingCode) setLocalCode(pendingCode);
  }, [lastIterationAt, pendingCode]);

  // Auto-scroll chat
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [iterationChat]);

  const handleChatSend = () => {
    if (!chatInput.trim() || isIterating) return;
    iterateCode(workflowId, chatInput.trim());
    setChatInput('');
  };

  const handleRegenerate = () => {
    iterateCode(
      workflowId,
      "Regenerate this file completely from scratch based on the original task. Make it fully functional and complete."
    );
  };

  const handleApprove = () => {
    approveCode(workflowId, localCode); // localCode may have manual Monaco edits
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="w-full h-full flex flex-col gap-4"
    >
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-white font-medium flex items-center gap-2">
            <FileCode size={16} className="text-amber-400" />
            Code Review
            <span className="text-xs text-zinc-500 font-normal ml-1">
              {pendingFilePath}
            </span>
          </h2>
          <p className="text-xs text-zinc-500 mt-0.5">
            Review and modify before pushing to GitHub
          </p>
        </div>
        <span className="text-xs bg-amber-500/10 text-amber-400 border border-amber-500/20 rounded-full px-3 py-1">
          Awaiting Your Approval
        </span>
      </div>

      {/* Two-column main area */}
      <div className="grid grid-cols-1 md:grid-cols-5 gap-4 flex-1 min-h-0">
        
        {/* LEFT: Monaco Editor — 3/5 width */}
        <div className="md:col-span-3 rounded-xl overflow-hidden border border-zinc-800 flex flex-col min-h-[500px]">
          <div className="flex items-center gap-2 px-4 py-2 bg-zinc-900 border-b border-zinc-800 text-xs text-zinc-500">
            <span className="w-3 h-3 rounded-full bg-zinc-700" />
            <span>{pendingFilePath}</span>
            <span className="ml-auto text-zinc-600">Editable — changes saved on approve</span>
          </div>
          <div style={{ height: '500px' }} className="flex-1">
            <Editor
              height="100%"
              language={pendingLanguage ?? 'plaintext'}
              value={localCode}
              onChange={(value) => setLocalCode(value ?? '')}
              theme="vs-dark"
              options={{
                minimap: { enabled: false },
                fontSize: 13,
                fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
                scrollBeyondLastLine: false,
                lineNumbers: 'on',
                wordWrap: 'on',
                padding: { top: 16, bottom: 16 },
                renderLineHighlight: 'gutter',
              }}
            />
          </div>
        </div>

        {/* RIGHT: Chat panel — 2/5 width */}
        <div className="md:col-span-2 rounded-xl border border-zinc-800 bg-zinc-900/50 flex flex-col min-h-[500px]">
          
          {/* Chat header */}
          <div className="px-4 py-3 border-b border-zinc-800">
            <p className="text-sm text-white font-medium flex items-center gap-2">
              <Sparkles size={14} className="text-violet-400" />
              Modify with AI
            </p>
            <p className="text-xs text-zinc-500 mt-0.5">
              Describe what to change — AI rewrites, you approve
            </p>
          </div>

          {/* Messages area */}
          <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-3">
            {iterationChat.length === 0 && (
              <div className="text-center text-xs text-zinc-600 mt-8">
                <p>Ask AI to modify the code</p>
                <p className="mt-1 text-zinc-700">e.g. "make it dark themed" or "add error handling"</p>
              </div>
            )}
            {iterationChat.map((msg) => (
              <div 
                key={msg.id}
                className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
              >
                <div className={`max-w-[85%] rounded-xl px-3 py-2 text-xs leading-relaxed ${
                  msg.role === 'user' 
                    ? 'bg-violet-600/80 text-white rounded-br-sm' 
                    : 'bg-zinc-800 text-zinc-300 rounded-bl-sm'
                }`}>
                  {msg.content}
                </div>
              </div>
            ))}
            {isIterating && (
              <div className="flex justify-start">
                <div className="bg-zinc-800 rounded-xl rounded-bl-sm px-3 py-2 text-xs text-zinc-400 flex items-center gap-2">
                  <Loader2 size={11} className="animate-spin" />
                  Rewriting code...
                </div>
              </div>
            )}
            <div ref={chatEndRef} />
          </div>

          {/* Chat input */}
          <div className="p-3 border-t border-zinc-800 flex gap-2">
            <input
              type="text"
              value={chatInput}
              onChange={(e) => setChatInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey && chatInput.trim() && !isIterating) {
                  handleChatSend();
                }
              }}
              placeholder="Ask for changes..."
              disabled={isIterating}
              className="flex-1 bg-zinc-800 border border-zinc-700 rounded-lg px-3 py-2 text-xs text-white placeholder:text-zinc-600 focus:outline-none focus:border-violet-500 disabled:opacity-50 transition-colors"
            />
            <button
              onClick={handleChatSend}
              disabled={!chatInput.trim() || isIterating}
              className="bg-violet-600 hover:bg-violet-500 disabled:opacity-40 disabled:cursor-not-allowed text-white rounded-lg px-3 py-2 text-xs font-medium transition-colors flex items-center gap-1"
            >
              <Send size={11} />
            </button>
          </div>
        </div>
      </div>

      {/* Bottom action bar */}
      <div className="flex items-center justify-between pt-2 border-t border-zinc-800">
        <button
          onClick={handleRegenerate}
          disabled={isIterating || isApproving}
          className="flex items-center gap-2 text-sm text-zinc-400 hover:text-white disabled:opacity-40 transition-colors"
        >
          <RefreshCw size={14} />
          Regenerate from scratch
        </button>

        <button
          onClick={handleApprove}
          disabled={isApproving || isIterating}
          className="flex items-center gap-2 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium rounded-lg px-5 py-2.5 transition-colors"
        >
          {isApproving ? (
            <>
              <Loader2 size={14} className="animate-spin" />
              Pushing to GitHub...
            </>
          ) : (
            <>
              <GitBranch size={14} />
              Push to GitHub →
            </>
          )}
        </button>
      </div>
    </motion.div>
  );
}
