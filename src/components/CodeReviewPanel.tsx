import React, { useState, useEffect, useRef } from 'react';
import Editor from '@monaco-editor/react';
import { motion } from 'framer-motion';
import {
  FileCode, FileText, File, GitBranch, Sparkles, Send,
  Loader2, RefreshCw, Code2, ChevronRight,
} from 'lucide-react';
import { useWorkflowStore, FileTreeItem } from '../store/workflowStore';

// ── Helpers ──────────────────────────────────────────────────────────────────

function detectLanguage(filePath: string | null): string {
  if (!filePath) return 'plaintext';
  const p = filePath.toLowerCase();
  if (p.endsWith('.py'))   return 'python';
  if (p.endsWith('.js') || p.endsWith('.jsx')) return 'javascript';
  if (p.endsWith('.ts') || p.endsWith('.tsx')) return 'typescript';
  if (p.endsWith('.java')) return 'java';
  if (p.endsWith('.html') || p.endsWith('.htm')) return 'html';
  if (p.endsWith('.css'))  return 'css';
  if (p.endsWith('.md'))   return 'markdown';
  if (p.endsWith('.json')) return 'json';
  if (p.endsWith('.xml'))  return 'xml';
  if (p.endsWith('.yml') || p.endsWith('.yaml')) return 'yaml';
  if (p.endsWith('.sh'))   return 'shell';
  return 'plaintext';
}

function FileIcon({ path }: { path: string }) {
  const p = path.toLowerCase();
  if (p.endsWith('.md') || p.endsWith('.txt')) return <FileText size={11} className="shrink-0 text-zinc-500" />;
  const codeExts = ['.ts', '.tsx', '.js', '.jsx', '.py', '.java', '.html', '.css', '.sh', '.json', '.yml', '.yaml', '.xml'];
  if (codeExts.some(ext => p.endsWith(ext))) return <FileCode size={11} className="shrink-0 text-zinc-500" />;
  return <File size={11} className="shrink-0 text-zinc-500" />;
}

/** Groups a flat file list by top-level directory. */
function groupByDir(files: FileTreeItem[]): Map<string, FileTreeItem[]> {
  const groups = new Map<string, FileTreeItem[]>();
  for (const f of files) {
    const parts = f.path.split('/');
    const dir = parts.length > 1 ? parts[0] : '';
    if (!groups.has(dir)) groups.set(dir, []);
    groups.get(dir)!.push(f);
  }
  return groups;
}

// ── Panel 1: File Tree ────────────────────────────────────────────────────────

function FileTreePanel({ workflowId }: { workflowId: string }) {
  const {
    repoFileTree,
    repoOwner,
    repoName,
    openFiles,
    modifiedFiles,
    activeEditorFile,
    isFetchingFile,
    openRepoFile,
  } = useWorkflowStore();

  const [fetchingPath, setFetchingPath] = useState<string | null>(null);

  const handleClick = async (filePath: string) => {
    if (isFetchingFile) return;
    setFetchingPath(filePath);
    await openRepoFile(workflowId, filePath);
    setFetchingPath(null);
  };

  const groups = groupByDir(repoFileTree);

  return (
    <div className="w-[20%] min-w-[160px] border-r border-zinc-800 bg-zinc-950 flex flex-col overflow-hidden">
      {/* Header */}
      <div className="px-3 py-2 border-b border-zinc-800 bg-zinc-950 shrink-0">
        <p className="text-[11px] text-zinc-400 font-medium flex items-center gap-1.5 truncate">
          <GitBranch size={11} className="shrink-0" />
          {repoOwner && repoName ? `${repoOwner}/${repoName}` : 'Repository'}
        </p>
      </div>

      {/* File list */}
      <div className="flex-1 overflow-y-auto py-1">
        {repoFileTree.length === 0 ? (
          <div className="px-3 py-6 text-center">
            <p className="text-[11px] text-zinc-700">No file tree available</p>
          </div>
        ) : (
          Array.from(groups.entries()).map(([dir, files]) => (
            <div key={dir || '__root__'}>
              {dir && (
                <div className="flex items-center gap-1 px-3 py-1 mt-1">
                  <ChevronRight size={10} className="text-zinc-700" />
                  <span className="text-[10px] text-zinc-600 font-medium uppercase tracking-wider truncate">{dir}</span>
                </div>
              )}
              {files.map((file) => {
                const isActive = activeEditorFile === file.path;
                const isOpen = openFiles[file.path] !== undefined;
                const isModified = isOpen && modifiedFiles[file.path] !== openFiles[file.path];
                const isFetching = fetchingPath === file.path;

                return (
                  <button
                    key={file.path}
                    onClick={() => handleClick(file.path)}
                    className={`w-full flex items-center gap-1.5 px-3 py-1.5 cursor-pointer text-[11px] hover:bg-zinc-800 transition-colors text-left group
                      ${isActive ? 'bg-zinc-800 text-white' : 'text-zinc-400'}`}
                    style={{ paddingLeft: dir ? '1.5rem' : '0.75rem' }}
                    title={file.path}
                  >
                    {isFetching ? (
                      <Loader2 size={11} className="animate-spin text-violet-400 shrink-0" />
                    ) : (
                      <FileIcon path={file.path} />
                    )}
                    <span className="truncate flex-1">
                      {(() => {
                        const parts = file.path.split('/')
                        if (parts.length === 1) return parts[0]
                        return parts[parts.length - 2] + '/' + parts[parts.length - 1]
                      })()}
                    </span>
                    {isModified && (
                      <span className="w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0 ml-auto" title="Modified" />
                    )}
                    {isOpen && !isModified && (
                      <span className="w-1.5 h-1.5 rounded-full bg-blue-500 shrink-0 ml-auto" title="Open" />
                    )}
                  </button>
                );
              })}
            </div>
          ))
        )}
      </div>
    </div>
  );
}

// ── Panel 2: Monaco Editor ────────────────────────────────────────────────────

function EditorPanel({
  localCode,
  setLocalCode,
}: {
  localCode: string;
  setLocalCode: (v: string) => void;
}) {
  const {
    openFiles,
    modifiedFiles,
    activeEditorFile,
    pendingFilePath,
    pendingLanguage,
    setActiveEditorFile,
    updateFileContent,
  } = useWorkflowStore();

  const openTabs = Object.keys(openFiles);
  const language = activeEditorFile
    ? detectLanguage(activeEditorFile)
    : (pendingLanguage ?? 'plaintext');

  const editorValue = activeEditorFile
    ? (modifiedFiles[activeEditorFile] ?? openFiles[activeEditorFile] ?? '')
    : localCode;

  const handleChange = (val: string | undefined) => {
    const v = val ?? '';
    if (activeEditorFile) {
      if (activeEditorFile === pendingFilePath) setLocalCode(v);
      updateFileContent(activeEditorFile, v);
    } else {
      setLocalCode(v);
    }
  };

  return (
    <div className="flex-1 flex flex-col overflow-hidden border-r border-zinc-800">
      {/* Tab bar */}
      <div className="flex items-center overflow-x-auto border-b border-zinc-800 bg-zinc-950 shrink-0 scrollbar-thin scrollbar-track-zinc-950 scrollbar-thumb-zinc-800">
        {openTabs.length === 0 ? (
          <span className="px-4 py-2 text-[11px] text-zinc-700 italic">No files open</span>
        ) : (
          openTabs.map((filePath) => {
            const isActive = activeEditorFile === filePath;
            const isModified = modifiedFiles[filePath] !== openFiles[filePath];
            return (
              <button
                key={filePath}
                onClick={() => setActiveEditorFile(filePath)}
                className={`px-3 py-2 text-[11px] border-r border-zinc-800 flex items-center gap-1.5 shrink-0 transition-colors
                  ${isActive
                    ? 'bg-zinc-900 text-white border-b-2 border-b-violet-500'
                    : 'bg-zinc-950 text-zinc-500 hover:text-zinc-300 border-b-2 border-b-transparent'}`}
              >
                {isModified && <span className="w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" />}
                <span className="max-w-[120px] truncate">
                  {(() => {
                    const parts = filePath.split('/')
                    if (parts.length === 1) return parts[0]
                    return parts[parts.length - 2] + '/' + parts[parts.length - 1]
                  })()}
                </span>
              </button>
            );
          })
        )}
      </div>

      {/* Editor body */}
      {activeEditorFile ? (
        <div className="flex-1 overflow-hidden">
          <Editor
            height="100%"
            language={language}
            value={editorValue}
            onChange={handleChange}
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
      ) : (
        <div className="flex-1 flex items-center justify-center text-zinc-600">
          <div className="text-center">
            <Code2 size={32} className="mx-auto mb-3 opacity-30" />
            <p className="text-sm">Select a file from the tree</p>
            <p className="text-xs mt-1 text-zinc-700">or the AI-generated file is shown automatically</p>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Panel 3: Agent / Chat ─────────────────────────────────────────────────────

function AgentPanel({ workflowId }: { workflowId: string }) {
  const {
    pendingFilePath,
    openFiles,
    iterationChat,
    isIterating,
    iterateCode,
  } = useWorkflowStore();

  const [chatInput, setChatInput] = useState('');
  const [multiFileInstruction, setMultiFileInstruction] = useState('');
  const [selectedOtherFiles, setSelectedOtherFiles] = useState<string[]>([]);
  const chatEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [iterationChat]);

  const otherOpenFiles = Object.keys(openFiles).filter(f => f !== pendingFilePath);

  const toggleOtherFile = (filePath: string) => {
    setSelectedOtherFiles(prev =>
      prev.includes(filePath) ? prev.filter(f => f !== filePath) : [...prev, filePath]
    );
  };

  const handleChatSend = () => {
    if (!chatInput.trim() || isIterating) return;
    
    // Send which file is currently active in the editor
    const targetFile = activeEditorFile ?? pendingFilePath ?? undefined;
    
    iterateCode(workflowId, chatInput.trim(), targetFile);
    setChatInput('');
  };

  const handleMultiFileApply = () => {
    if (!multiFileInstruction.trim() || selectedOtherFiles.length === 0 || isIterating) return;
    
    // Apply to each selected file sequentially
    // For now: apply to first selected file (can be enhanced to loop)
    const targetFile = selectedOtherFiles[0];
    
    iterateCode(
      workflowId, 
      multiFileInstruction.trim(),
      targetFile
    );
    setMultiFileInstruction('');
  };

  return (
    <div className="w-[30%] min-w-[240px] flex flex-col overflow-hidden bg-zinc-900/50">
      {/* ── SECTION A: Single-file AI chat ── */}
      <div className="flex flex-col border-b border-zinc-800" style={{ height: '55%' }}>
        <div className="px-4 py-3 border-b border-zinc-800 shrink-0">
          <p className="text-[12px] text-white font-medium flex items-center gap-1.5">
            <Sparkles size={12} className="text-violet-400" />
            ✨ Modify with AI
          </p>
          <p className="text-[10px] text-zinc-500 mt-0.5">Describe what to change — AI rewrites, you approve</p>
        </div>

        {/* Chat messages */}
        <div className="flex-1 overflow-y-auto p-3 flex flex-col gap-2 min-h-0">
          {iterationChat.length === 0 && (
            <div className="text-center text-[11px] text-zinc-700 mt-6">
              <p>Ask AI to modify the code</p>
              <p className="mt-1 text-zinc-800">e.g. "make it dark themed" or "add error handling"</p>
            </div>
          )}
          {iterationChat.map((msg) => (
            <div key={msg.id} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
              <div className={`max-w-[90%] rounded-xl px-3 py-2 text-[11px] leading-relaxed ${
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
              <div className="bg-zinc-800 rounded-xl rounded-bl-sm px-3 py-2 text-[11px] text-zinc-400 flex items-center gap-2">
                <Loader2 size={10} className="animate-spin" />
                Rewriting code...
              </div>
            </div>
          )}
          <div ref={chatEndRef} />
        </div>

        {/* Chat input */}
        <div className="p-2 border-t border-zinc-800 flex gap-1.5 shrink-0">
          <input
            type="text"
            value={chatInput}
            onChange={(e) => setChatInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey && chatInput.trim() && !isIterating) handleChatSend();
            }}
            placeholder="Ask for changes..."
            disabled={isIterating}
            className="flex-1 bg-zinc-800 border border-zinc-700 rounded-lg px-2.5 py-1.5 text-[11px] text-white placeholder:text-zinc-600 focus:outline-none focus:border-violet-500 disabled:opacity-50 transition-colors"
          />
          <button
            onClick={handleChatSend}
            disabled={!chatInput.trim() || isIterating}
            className="bg-violet-600 hover:bg-violet-500 disabled:opacity-40 text-white rounded-lg px-2.5 py-1.5 transition-colors"
          >
            <Send size={11} />
          </button>
        </div>
      </div>

      {/* ── SECTION B: Multi-file agent ── */}
      <div className="flex flex-col flex-1 overflow-hidden">
        <div className="px-4 py-2.5 border-b border-zinc-800 shrink-0">
          <p className="text-[12px] text-white font-medium flex items-center gap-1.5">
            🔧 Modify Other Files
          </p>
          <p className="text-[10px] text-zinc-500 mt-0.5">Open files from the tree to edit them</p>
        </div>

        <div className="flex-1 overflow-y-auto p-3 flex flex-col gap-2 min-h-0">
          {otherOpenFiles.length === 0 ? (
            <p className="text-[11px] text-zinc-700 mt-3 text-center">
              Open files from the tree to modify them with AI
            </p>
          ) : (
            otherOpenFiles.map((filePath) => (
              <label key={filePath} className="flex items-center gap-2 text-[11px] text-zinc-400 py-1 cursor-pointer hover:text-zinc-200">
                <input
                  type="checkbox"
                  checked={selectedOtherFiles.includes(filePath)}
                  onChange={() => toggleOtherFile(filePath)}
                  className="accent-violet-500"
                />
                <span className="truncate">
                  {(() => {
                    const parts = filePath.split('/')
                    if (parts.length === 1) return parts[0]
                    return parts[parts.length - 2] + '/' + parts[parts.length - 1]
                  })()}
                </span>
                <span className="text-zinc-700 text-[9px] ml-auto truncate max-w-[60px]">{filePath}</span>
              </label>
            ))
          )}
        </div>

        <div className="p-2 flex flex-col gap-1.5 border-t border-zinc-800 shrink-0">
          <textarea
            value={multiFileInstruction}
            onChange={(e) => setMultiFileInstruction(e.target.value)}
            placeholder={`e.g. "Add dark mode styles to match the main file"`}
            disabled={isIterating || otherOpenFiles.length === 0}
            rows={2}
            className="w-full bg-zinc-800 border border-zinc-700 rounded-lg px-2.5 py-1.5 text-[11px] text-white placeholder:text-zinc-600 focus:outline-none focus:border-violet-500 disabled:opacity-40 resize-none transition-colors"
          />
          <button
            onClick={handleMultiFileApply}
            disabled={isIterating || !multiFileInstruction.trim() || selectedOtherFiles.length === 0}
            className="w-full bg-zinc-700 hover:bg-zinc-600 disabled:opacity-40 disabled:cursor-not-allowed text-white rounded-lg px-3 py-1.5 text-[11px] font-medium transition-colors"
          >
            Apply to Selected ({selectedOtherFiles.length})
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Root: CodeReviewPanel ─────────────────────────────────────────────────────

export default function CodeReviewPanel({ workflowId }: { workflowId: string }) {
  const {
    pendingCode,
    pendingFilePath,
    openFiles,
    modifiedFiles,
    isIterating,
    isApproving,
    iterateCode,
    approveCode,
    lastIterationAt,
    activeEditorFile,
  } = useWorkflowStore();

  const [localCode, setLocalCode] = useState(pendingCode ?? '');
  const chatEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (activeEditorFile && modifiedFiles[activeEditorFile] !== undefined) {
      setLocalCode(modifiedFiles[activeEditorFile]);
    }
  }, [activeEditorFile, lastIterationAt, modifiedFiles]);

  useEffect(() => {
    if (!pendingCode || !pendingFilePath || !workflowId) return;
    
    // Auto-open the main generated file so it appears in the file tree with correct state
    const { openFiles } = useWorkflowStore.getState();
    
    if (!openFiles[pendingFilePath]) {
      // We already have the content from the pause — register it as "open"
      // so the file tree shows it with a blue/orange dot
      useWorkflowStore.setState(state => ({
        openFiles: { 
          ...state.openFiles, 
          [pendingFilePath]: pendingCode  // original LLM output
        },
        modifiedFiles: {
          ...state.modifiedFiles,
          [pendingFilePath]: pendingCode  // start as same, tracks user edits
        },
        activeEditorFile: pendingFilePath
      }));
    }
  }, [pendingFilePath, pendingCode, workflowId]);

  useEffect(() => {
    return () => {
      // Reset IDE state when panel closes
      useWorkflowStore.setState({
        repoFileTree: [],
        openFiles: {},
        modifiedFiles: {},
        activeEditorFile: null,
        isFetchingFile: false
      });
    };
  }, []);

  const handleRegenerate = () => {
    iterateCode(
      workflowId,
      'Regenerate this file completely from scratch based on the original task. Make it fully functional and complete.'
    );
  };

  const handleApprove = () => {
    approveCode(workflowId, localCode);
  };

  // Count all modified files (main + additional)
  const modifiedCount = Object.keys(modifiedFiles).filter(
    (f) => modifiedFiles[f] !== openFiles[f]
  ).length;

  const totalModified = modifiedCount || (localCode !== pendingCode ? 1 : 0);

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      className="w-full flex flex-col"
    >
      {/* Breadcrumb / status header */}
      <div className="flex items-center justify-between mb-3">
        <div>
          <h2 className="text-white font-medium flex items-center gap-2 text-sm">
            <FileCode size={15} className="text-amber-400" />
            Code Review
            {pendingFilePath && (
              <span className="text-[11px] text-zinc-500 font-normal">{pendingFilePath}</span>
            )}
          </h2>
          <p className="text-[11px] text-zinc-500 mt-0.5">
            Review and modify before pushing to GitHub
          </p>
        </div>
        <span className="text-[11px] bg-amber-500/10 text-amber-400 border border-amber-500/20 rounded-full px-3 py-1">
          Awaiting Your Approval
        </span>
      </div>

      {/* ── Three-panel IDE layout ── */}
      <div className="flex h-[680px] rounded-xl border border-zinc-800 overflow-hidden">
        <FileTreePanel workflowId={workflowId} />
        <EditorPanel localCode={localCode} setLocalCode={setLocalCode} />
        <AgentPanel workflowId={workflowId} />
      </div>

      {/* ── Bottom action bar ── */}
      <div className="flex items-center justify-between px-4 py-3 mt-2 rounded-xl border border-zinc-800 bg-zinc-950">
        <div className="flex items-center gap-5 text-[11px] text-zinc-500">
          <span>{Object.keys(openFiles).length} file{Object.keys(openFiles).length !== 1 ? 's' : ''} open</span>
          {totalModified > 0 && (
            <span className="text-amber-400">{totalModified} modified</span>
          )}
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={handleRegenerate}
            disabled={isIterating || isApproving}
            className="text-[11px] text-zinc-400 hover:text-white disabled:opacity-40 flex items-center gap-1.5 transition-colors"
          >
            <RefreshCw size={11} /> Regenerate
          </button>

          <button
            onClick={handleApprove}
            disabled={isApproving || isIterating}
            className="flex items-center gap-2 bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium rounded-lg px-5 py-2 transition-colors"
          >
            {isApproving ? (
              <><Loader2 size={13} className="animate-spin" /> Pushing all changes...</>
            ) : (
              <><GitBranch size={13} /> Push {totalModified > 0 ? `${totalModified} file${totalModified !== 1 ? 's' : ''}` : 'code'} as PR →</>
            )}
          </button>
        </div>
      </div>
    </motion.div>
  );
}
