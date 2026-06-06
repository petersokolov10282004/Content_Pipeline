import type { PipelineRunEvent } from "@/types/pipeline";

const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
const MAX_RECONNECT_DELAY_MS = 30_000;

type Listener = (event: PipelineRunEvent) => void;
type CloseListener = () => void;

/**
 * SSE connection for a single pipeline run.
 * Automatically reconnects on error with exponential backoff.
 * Stops reconnecting after RUN_COMPLETED or RUN_FAILED events.
 */
export class PipelineEventSource {
  private es: EventSource | null = null;
  private listeners: Listener[] = [];
  private closeListeners: CloseListener[] = [];
  private reconnectDelay = 1000;
  private closed = false;

  constructor(private readonly projectId: string, private readonly runId: string) {}

  open() {
    if (this.closed) return;
    const url = `${BASE_URL}/api/v1/projects/${this.projectId}/pipeline-runs/${this.runId}/events`;
    this.es = new EventSource(url);

    this.es.addEventListener("pipeline-event", (e: MessageEvent) => {
      try {
        const event: PipelineRunEvent = JSON.parse(e.data);
        this.reconnectDelay = 1000; // reset on successful message
        this.listeners.forEach((fn) => fn(event));

        if (event.type === "RUN_COMPLETED" || event.type === "RUN_FAILED") {
          this.close();
        }
      } catch {
        // malformed event — ignore
      }
    });

    this.es.onerror = () => {
      this.es?.close();
      this.es = null;
      if (!this.closed) {
        setTimeout(() => this.open(), this.reconnectDelay);
        this.reconnectDelay = Math.min(this.reconnectDelay * 2, MAX_RECONNECT_DELAY_MS);
      }
    };
  }

  onEvent(listener: Listener) {
    this.listeners.push(listener);
    return () => { this.listeners = this.listeners.filter((l) => l !== listener); };
  }

  onClose(listener: CloseListener) {
    this.closeListeners.push(listener);
    return () => { this.closeListeners = this.closeListeners.filter((l) => l !== listener); };
  }

  close() {
    this.closed = true;
    this.es?.close();
    this.es = null;
    this.closeListeners.forEach((fn) => fn());
  }
}
