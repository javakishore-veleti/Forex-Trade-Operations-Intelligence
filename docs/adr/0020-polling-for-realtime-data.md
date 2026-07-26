# ADR-0020: Polling for Real-Time Data vs WebSocket vs Server-Sent Events

## Status
Accepted

## Context
Portal users need near-real-time visibility into trade state changes, risk updates, and EOD
progress. The Admin Portal's EOD dashboard refreshes readiness status; the TraderDesk shows
live position changes; the FXTradeBlotter displays settlement status updates.

Update frequency varies: EOD dashboard changes every 5 minutes, trade positions update every
10–30 seconds during market hours, and settlement status changes a few times per hour.

The solution must work through corporate proxies, load balancers, and CDNs without special
infrastructure configuration.

## Decision
Use **HTTP polling with adaptive intervals** as the primary real-time mechanism for all three portals.

```typescript
// Angular service using RxJS interval with adaptive backoff
pollTradeStatus(tradeId: string): Observable<TradeStatus> {
  return timer(0, this.calculateInterval()).pipe(
    switchMap(() => this.http.get<TradeStatus>(`/api/trades/${tradeId}/status`)),
    distinctUntilChanged((a, b) => a.version === b.version),
    shareReplay(1)
  );
}
```

Polling intervals:
- EOD dashboard: 30 seconds (low-frequency changes)
- Trade position grid: 10 seconds during market hours, 60 seconds after hours
- Settlement status: 30 seconds
- Adaptive backoff: if 5 consecutive responses unchanged, double interval (cap at 120s)

## Alternatives Considered

| Alternative | Why rejected |
|-------------|-------------|
| **WebSocket** | Requires sticky sessions or Redis pub/sub for multi-instance backends; corporate proxies often terminate idle WebSocket connections; adds connection lifecycle management complexity |
| **Server-Sent Events (SSE)** | Better fit than WebSocket for unidirectional updates, but HTTP/2 multiplexing complicates load balancer configuration; reconnection logic needed; less universal proxy support than plain HTTP |
| **GraphQL subscriptions** | Requires WebSocket transport underneath; adds GraphQL infrastructure; over-engineered for the update patterns we have |
| **Long polling** | Holds server threads open; harder to scale; timeout tuning conflicts with load balancer idle timeouts |

## Consequences

### Positive
- Works through all proxies, CDNs, and load balancers without configuration
- Stateless backend — no connection registry, no session affinity required
- Simple to implement, debug, and monitor (standard HTTP requests in browser DevTools)
- `distinctUntilChanged` prevents unnecessary re-renders when data hasn't changed
- Adaptive backoff reduces load during quiet periods automatically

### Negative
- Higher latency than push-based solutions (up to interval duration)
- More HTTP requests than necessary when data hasn't changed (mitigated by ETag/304)
- Not suitable for sub-second updates (not required by our use cases)

### Mitigations
- Backend returns `ETag` headers; unchanged responses return `304 Not Modified` (minimal bandwidth)
- Polling paused when browser tab is inactive (`document.visibilityState`)
- If future requirements demand sub-second push, WebSocket can be added for specific high-frequency feeds
  without changing the polling-based architecture for other views
