import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from '@/shared/components/Layout';
import { AllContentsPage } from '@/pages/feed/AllContents';
import { ContentWithCommentPage } from '@/pages/feed/ContentsWithComment';
import { AlbumListPage } from '@/pages/albums/AlbumListPage';
import { AlbumCreatePage } from '@/pages/albums/AlbumCreatePage';

function App() {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/" element={<Navigate to="/pages/all" replace />} />
                <Route
                    path="/pages/all"
                    element={
                        <Layout>
                            <AllContentsPage />
                        </Layout>
                    }
                />
                <Route
                    path="/pages/comment"
                    element={
                        <Layout>
                            <ContentWithCommentPage />
                        </Layout>
                    }
                />
                <Route
                    path="/pages/albums"
                    element={
                        <Layout>
                            <AlbumListPage />
                        </Layout>
                    }
                />
                <Route
                    path="/pages/albums/new"
                    element={
                        <Layout>
                            <AlbumCreatePage />
                        </Layout>
                    }
                />

                {/* 互換用リダイレクト */}
                <Route path="/comment" element={<Navigate to="/pages/comment" replace />} />
                <Route path="/albums" element={<Navigate to="/pages/albums" replace />} />
                <Route path="/photobook" element={<Navigate to="/pages/albums" replace />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;
