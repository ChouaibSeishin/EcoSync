export type IconName = 'grid' | 'connections' | 'spark' | 'pulse' | 'shield' | 'arrow' | 'logout' | 'menu' | 'close' | 'database' | 'check';
const paths: Record<IconName, string> = {
  grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  connections: 'M8 3H3v5 M16 3h5v5 M3 16v5h5 M21 16v5h-5 M7 12h10 M12 7v10',
  spark: 'm12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5Z',
  pulse: 'M2 12h4l3-8 5 16 3-8h5',
  shield: 'm12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6z M8 12l3 3 5-6',
  arrow: 'M5 12h14 m-6-6 6 6-6 6',
  logout: 'M9 4H4v16h5 M10 12h11 m-5-5 5 5-5 5',
  menu: 'M4 6h16 M4 12h16 M4 18h16',
  close: 'm6 6 12 12 M6 18 18 6',
  database: 'M20 6c0 2-3.6 3-8 3S4 8 4 6s3.6-3 8-3 8 1 8 3Z M4 6v12c0 2 3.6 3 8 3s8-1 8-3V6 M4 12c0 2 3.6 3 8 3s8-1 8-3',
  check: 'm5 12 4 4L19 6',
};
export function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]}/></svg>;
}
export function Brand() {
  return <span className="brand"><span className="brand-symbol"><svg viewBox="0 0 32 32" aria-hidden="true"><path d="M7 16a9 9 0 0 1 15-7M25 16a9 9 0 0 1-15 7" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round"/><path d="m19 5 4 4-5 1M13 27l-4-4 5-1" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"/></svg></span><span>Ecosync<span className="brand-ai">AI</span></span></span>;
}
