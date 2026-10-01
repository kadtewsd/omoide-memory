import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from '@/shared/components/Layout';
import { AllContentsPage } from '@/pages/feed/AllContents.tsx';
import { ContentWithCommentPage } from '@/pages/feed/ContentsWithComment.tsx';
import { AlbumListPage } from '@/pages/albums/AlbumListPage';

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
                            <AlbumListPage />
                        </Layout>
                    }
                />
                <Route path="/photobook" element={<Navigate to="/albums" replace />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;
