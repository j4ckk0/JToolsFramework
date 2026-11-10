/*
 * @jtools/web: the page's side of JToolsFramework — what a page needs to be the view of an org.jtools server. A project
 * gets these files unpacked into frontend/target/jtools-web by its build (jtools-parent, profile jtools-frontend) and
 * imports them as '@jtools/web' (tsconfig.json: "paths").
 */
export { apiErrorMessage } from './api-error';
export { PagePresenceService } from './page-presence.service';
export { provideJTools } from './provide-jtools';
