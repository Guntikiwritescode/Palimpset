import { BrowserRouter, Link, Route, Routes } from "react-router-dom";
import { Tour } from "./components/Tour";
import { SearchRoute } from "./routes/Search";
import { EntityRoute } from "./routes/Entity";
import { PairRoute } from "./routes/Pair";
import { ClaimRoute } from "./routes/Claim";
import { AboutRoute } from "./routes/About";
import { NotFoundRoute } from "./routes/NotFound";

export function App() {
  return (
    <BrowserRouter>
      <div className="app">
        <header className="topbar">
          <span className="brand">
            <Link to="/">PALIMPSEST</Link>{" "}
            <span className="muted small">· internal explorer</span>
          </span>
          <nav aria-label="Primary">
            <Link to="/">Search</Link>
            <Link to="/about">About the numbers</Link>
          </nav>
        </header>
        <main>
          <Routes>
            <Route path="/" element={<SearchRoute />} />
            <Route path="/entity/:id" element={<EntityRoute />} />
            <Route path="/pair/:a/:b" element={<PairRoute />} />
            <Route path="/claim/:id" element={<ClaimRoute />} />
            <Route path="/about" element={<AboutRoute />} />
            <Route path="*" element={<NotFoundRoute />} />
          </Routes>
        </main>
        <Tour />
      </div>
    </BrowserRouter>
  );
}
