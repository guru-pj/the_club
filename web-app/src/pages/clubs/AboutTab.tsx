import type { Club } from '../../api/types'
import { LinkIcon, PinIcon, ShieldIcon } from '../../components/icons'
import { Card, EmptyState } from '../../components/ui'
import { formatDate, humanize } from '../../lib/format'
import { mapLink } from '../../lib/location'
import { isHttpUrl } from './urls'

/** The club's public profile: what it is, where it meets, how to reach it, and its rules. */
export function AboutTab({ club }: { club: Club }) {
  const links = Object.entries(club.socialLinks ?? {}).filter(([, url]) => isHttpUrl(url))
  const hasContact = club.contactEmail || club.contactPhone || club.website || links.length > 0
  return (
    <div className="layout-sidebar">
      <div className="stack-lg">
        <Card title="About the club">
          {club.description ? <p className="pre-wrap">{club.description}</p> : <EmptyState title="No description yet" />}
        </Card>
        <Card title="Club rules">
          {club.rules ? <p className="pre-wrap">{club.rules}</p> : <p className="muted">The club hasn't published rules yet.</p>}
        </Card>
      </div>
      <div className="stack-lg">
        <Card title="Details">
          <dl className="kv">
            {club.clubType && <><dt>Category</dt><dd>{humanize(club.clubType)}</dd></>}
            <dt>Meets at</dt>
            <dd>
              {club.address ?? club.city ?? '—'}
              {club.latitude != null && club.longitude != null && (
                <div><a href={mapLink(club.latitude, club.longitude)} target="_blank" rel="noreferrer"><PinIcon size={14} /> View on map</a></div>
              )}
            </dd>
            {club.verified && club.verifiedAt && (
              <><dt>Verified</dt><dd className="row" style={{ gap: 4 }}><ShieldIcon size={14} />{formatDate(club.verifiedAt)}</dd></>
            )}
          </dl>
        </Card>
        <Card title="Contact">
          {!hasContact ? <p className="muted">No contact details yet.</p> : (
            <div className="stack" style={{ gap: 6 }}>
              {club.contactEmail && <a href={`mailto:${club.contactEmail}`}>{club.contactEmail}</a>}
              {club.contactPhone && <a href={`tel:${club.contactPhone.replace(/[^+0-9]/g, '')}`}>{club.contactPhone}</a>}
              {club.website && isHttpUrl(club.website) && (
                <a href={club.website} target="_blank" rel="noopener noreferrer"><LinkIcon size={14} /> Website</a>
              )}
              {links.map(([network, url]) => (
                <a key={network} href={url} target="_blank" rel="noopener noreferrer"><LinkIcon size={14} /> {humanize(network)}</a>
              ))}
            </div>
          )}
        </Card>
      </div>
    </div>
  )
}
