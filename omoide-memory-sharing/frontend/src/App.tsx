import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Layout } from './components/Layout';
import { AllContentsPage } from './pages/all-contents';
import { ContentWithCommentPage } from './pages/content-with-comment';
import { PhotobookListPage } from './pages/photobook-list';
import { PhotobookPage } from './pages/photobook';

function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route
                    path="/"
                    element={
                        <Layout>
                            <AllContentsPage />
                        </Layout>
                    }
                />
                <Route
                    path="/comment"
                    element={
                        <Layout>
                            <ContentWithCommentPage />
                        </Layout>
                    }
                />
                <Route
                    path="/albums"
                    element={
                        <Layout>
                            <PhotobookListPage />
                        </Layout>
                    }
                />
                <Route path="/photobook" element={<PhotobookPage />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;
