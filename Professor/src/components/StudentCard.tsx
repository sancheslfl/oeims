import { memo } from 'react';
import type { ParticipantResponse, EventResponse } from '../types';

interface Props {
  participant: ParticipantResponse;
  events: EventResponse[];
}

function borderClass(count: number) {
  if (count === 0) return 'card-ok';
  if (count <= 2) return 'card-warn';
  return 'card-danger';
}

function badgeClass(count: number) {
  if (count <= 2) return 'badge-warn';
  return 'badge-danger';
}

function severityIcon(s: string) {
  if (s === 'CRITICAL') return '🔴';
  if (s === 'WARNING') return '🟡';
  return '🔵';
}

function fmtTime(iso: string) {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

export const StudentCard = memo(function StudentCard({ participant, events }: Props) {
  const bc = borderClass(events.length);

  const dotClass =
    participant.connectionStatus === 'CONNECTED'
      ? 'dot-connected'
      : participant.connectionStatus === 'TIMED_OUT'
      ? 'dot-timeout'
      : 'dot-disconnected';

  return (
    <div className={`student-card ${bc}`}>
      <div className="card-main">
        <span className={`conn-dot ${dotClass}`} title={participant.connectionStatus} />
        <span className="student-email" title={participant.email}>
          {participant.email}
        </span>
        {events.length > 0 && (
          <span className={`violation-badge ${badgeClass(events.length)}`}>
            {events.length}
          </span>
        )}
      </div>

      <div className="card-tooltip">
        <p className="tt-email">{participant.email}</p>
        <p className="tt-row">
          Status: <strong>{participant.connectionStatus}</strong>
        </p>
        {participant.lastHeartbeat && (
          <p className="tt-row">Last heartbeat: {fmtTime(participant.lastHeartbeat)}</p>
        )}
        <p className="tt-row">Joined: {fmtTime(participant.joinedAt)}</p>

        {events.length === 0 ? (
          <p className="tt-clean">No violations</p>
        ) : (
          <ul className="tt-violations">
            {events.map(ev => (
              <li key={ev.id} className="tt-event">
                <span className="te-icon">{severityIcon(ev.severity)}</span>
                <span className="te-monitor">[{ev.monitorName}]</span>
                <span className="te-msg">{ev.message}</span>
                <span className="te-time">{fmtTime(ev.occurredAt)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
});
